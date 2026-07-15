package com.sfa.service;

import com.sfa.dto.ret.CreateReturnRequest;
import com.sfa.dto.ret.ReturnItemRequest;
import com.sfa.entity.Customer;
import com.sfa.entity.Order;
import com.sfa.entity.Product;
import com.sfa.entity.Return;
import com.sfa.entity.ReturnItem;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.OrderRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.ReturnRepository;
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
public class ReturnService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReturnRepository returnRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public Page<Return> list(Pageable pageable) {
        UserDetailsImpl principal = currentUser();
        if (principal.getRoleName().equals("SALES_REP")) {
            return returnRepository.findBySalesRepId(principal.getId(), pageable);
        }
        return returnRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Return> getCustomerReturns(UUID customerId, Pageable pageable) {
        return returnRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional
    public Return create(CreateReturnRequest req) {
        Order order = null;
        Customer customer;
        if (req.orderId() != null) {
            order = orderRepository.findById(req.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", req.orderId()));
            customer = order.getCustomer();
        } else if (req.customerId() != null) {
            customer = customerRepository.findById(req.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId()));
        } else {
            throw new BusinessException("Either orderId or customerId must be provided");
        }

        UserDetailsImpl principal = currentUser();
        User salesRep = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));

        Return ret = new Return();
        ret.setReturnNumber(generateReturnNumber());
        ret.setCustomer(customer);
        ret.setOrder(order);
        ret.setSalesRep(salesRep);
        ret.setReason(req.reason());
        ret.setStatus(Return.ReturnStatus.PENDING);
        ret.setReturnDate(Instant.now());

        List<ReturnItem> items = new ArrayList<>();
        for (ReturnItemRequest itemReq : req.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.productId()));
            ReturnItem item = new ReturnItem();
            item.setProduct(product);
            item.setQuantity(itemReq.quantity());
            item.setReturnHeader(ret);
            items.add(item);
        }
        ret.setItems(items);

        return returnRepository.save(ret);
    }

    private String generateReturnNumber() {
        String date   = LocalDate.now(ZoneId.of("UTC")).format(DATE_FMT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "RET-" + date + "-" + suffix;
    }

    @Transactional
    public Return updateStatus(UUID id, String status) {
        Return ret = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));
        try {
            ret.setStatus(Return.ReturnStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid return status: " + status);
        }
        return returnRepository.save(ret);
    }

    private UserDetailsImpl currentUser() {
        return (UserDetailsImpl) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
