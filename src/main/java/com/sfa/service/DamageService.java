package com.sfa.service;

import com.sfa.dto.damage.CreateDamageRequest;
import com.sfa.dto.damage.DamageItemRequest;
import com.sfa.entity.Customer;
import com.sfa.entity.Damage;
import com.sfa.entity.DamageItem;
import com.sfa.entity.Product;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.DamageRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DamageService {

    private final DamageRepository damageRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ReturnDamageNoteGenerator noteGenerator;
    private final PricingEngine pricingEngine;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Damage getById(UUID id) {
        return damageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Damage", id));
    }

    public byte[] getPdfBytes(UUID id) {
        try {
            return noteGenerator.generateDamagePdf(getById(id));
        } catch (java.io.IOException ex) {
            throw new BusinessException("Failed to generate PDF for damage " + id);
        }
    }

    public byte[] getThermalBytes(UUID id) {
        return noteGenerator.generateDamageThermal(getById(id));
    }

    /**
     * TEMPORARY — dev/QA aid, remove before production (see InvoiceService's
     * matching getThermalPreviewBytes). Narrow receipt-style PDF mirroring the
     * exact thermal-print content/layout, for the mobile app's print-preview
     * screen — not persisted, cheap to regenerate.
     */
    public byte[] getThermalPreviewBytes(UUID id) {
        Damage damage = getById(id);
        try {
            return noteGenerator.generateDamageThermalPreview(damage);
        } catch (java.io.IOException ex) {
            throw new BusinessException("Failed to generate print preview for damage " + damage.getDamageNumber());
        }
    }

    @Transactional
    public Damage recordPrint(UUID id) {
        Damage damage = getById(id);
        damage.incrementPrintCount();
        return damageRepository.save(damage);
    }

    public Page<Damage> list(Pageable pageable) {
        UserDetailsImpl principal = currentUser();
        if (principal.getRoleName().equals("SALES_REP")) {
            return damageRepository.findByReportedById(principal.getId(), pageable);
        }
        return damageRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Damage> getCustomerDamages(UUID customerId, Pageable pageable) {
        return damageRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional
    public Damage create(CreateDamageRequest req) {
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId()));

        UserDetailsImpl principal = currentUser();
        User reporter = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));

        Damage damage = new Damage();
        damage.setDamageNumber(generateDamageNumber(customer));
        damage.setCustomer(customer);
        damage.setReportedBy(reporter);
        damage.setDescription(req.description());
        damage.setStatus(Damage.DamageStatus.PENDING);
        damage.setDamageDate(Instant.now());

        List<DamageItem> items = new ArrayList<>();
        for (DamageItemRequest itemReq : req.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.productId()));
            PricingEngine.PriceResult price = pricingEngine.resolve(
                    itemReq.productId(), customer.getId(), itemReq.quantity(), itemReq.batchPriceId());
            DamageItem item = new DamageItem();
            item.setProduct(product);
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(price.unitPrice());
            item.setPriceSource(price.source());
            item.setDamageHeader(damage);
            items.add(item);
        }
        damage.setItems(items);

        return damageRepository.save(damage);
    }

    private String generateDamageNumber(Customer customer) {
        long seq = ((Number) em.createNativeQuery("SELECT NEXTVAL('damage_number_seq')").getSingleResult()).longValue();
        return "DMG_IT" + resolveLocationLetter(customer) + "_" + "%05d".formatted(seq);
    }

    /**
     * First letter of the customer's location (e.g. "Kandy" -> "K"), falling back to
     * "X" when missing — mirrors InvoiceService's resolveInvoiceCode so numbering
     * never fails on missing data.
     */
    private String resolveLocationLetter(Customer customer) {
        String location = customer.getLocation();
        return (location != null && !location.isBlank())
                ? location.trim().substring(0, 1).toUpperCase(Locale.ENGLISH)
                : "X";
    }

    @Transactional
    public Damage updateStatus(UUID id, String status) {
        Damage damage = damageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Damage", id));
        try {
            damage.setStatus(Damage.DamageStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid damage status: " + status);
        }
        return damageRepository.save(damage);
    }

    private UserDetailsImpl currentUser() {
        return (UserDetailsImpl) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
