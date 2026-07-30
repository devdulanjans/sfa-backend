package com.sfa.service;

import com.sfa.dto.CreateVendorRequest;
import com.sfa.dto.VendorDto;
import com.sfa.entity.Vendor;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VendorService {

    private final VendorRepository vendorRepo;

    @Transactional(readOnly = true)
    public Page<VendorDto> list(String search, Pageable pageable) {
        return vendorRepo.search(search, pageable).map(VendorDto::from);
    }

    public VendorDto create(CreateVendorRequest req) {
        if (req.vendorCode() == null || req.vendorCode().isBlank()) {
            throw new BusinessException("Vendor code is required");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new BusinessException("Vendor name is required");
        }
        if (vendorRepo.existsByVendorCode(req.vendorCode())) {
            throw new BusinessException("Vendor code already exists: " + req.vendorCode());
        }
        Vendor vendor = Vendor.builder()
                .vendorCode(req.vendorCode())
                .name(req.name())
                .contactPerson(req.contactPerson())
                .phone(req.phone())
                .email(req.email())
                .address(req.address())
                .taxNumber(req.taxNumber())
                .paymentTermsDays(req.paymentTermsDays() != null ? req.paymentTermsDays() : 30)
                .build();
        return VendorDto.from(vendorRepo.save(vendor));
    }

    public VendorDto update(UUID id, CreateVendorRequest req) {
        Vendor vendor = vendorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", id));
        vendor.setName(req.name());
        vendor.setContactPerson(req.contactPerson());
        vendor.setPhone(req.phone());
        vendor.setEmail(req.email());
        vendor.setAddress(req.address());
        vendor.setTaxNumber(req.taxNumber());
        if (req.paymentTermsDays() != null) {
            vendor.setPaymentTermsDays(req.paymentTermsDays());
        }
        return VendorDto.from(vendorRepo.save(vendor));
    }

    public void setActive(UUID id, boolean active) {
        Vendor vendor = vendorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", id));
        vendor.setActive(active);
        vendorRepo.save(vendor);
    }
}
