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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DamageService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DamageRepository damageRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

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
        damage.setDamageNumber(generateDamageNumber());
        damage.setCustomer(customer);
        damage.setReportedBy(reporter);
        damage.setDescription(req.description());
        damage.setStatus(Damage.DamageStatus.PENDING);
        damage.setDamageDate(Instant.now());

        List<DamageItem> items = new ArrayList<>();
        for (DamageItemRequest itemReq : req.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.productId()));
            DamageItem item = new DamageItem();
            item.setProduct(product);
            item.setQuantity(itemReq.quantity());
            item.setDamageHeader(damage);
            items.add(item);
        }
        damage.setItems(items);

        return damageRepository.save(damage);
    }

    private String generateDamageNumber() {
        String date   = LocalDate.now(ZoneId.of("UTC")).format(DATE_FMT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "DMG-" + date + "-" + suffix;
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
