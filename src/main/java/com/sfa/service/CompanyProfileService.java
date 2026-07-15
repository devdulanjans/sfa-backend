package com.sfa.service;

import com.sfa.dto.CompanyProfileDto;
import com.sfa.dto.CompanyProfileUpdateRequest;
import com.sfa.entity.CompanyProfile;
import com.sfa.exception.BusinessException;
import com.sfa.repository.CompanyProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyProfileService {

    private static final String LOGO_OBJECT_PATH = "company-profile/logo";
    private static final long   MAX_LOGO_BYTES    = 2L * 1024 * 1024;

    private final CompanyProfileRepository companyProfileRepo;
    private final MinioStorageService      storage;

    @Transactional(readOnly = true)
    public CompanyProfileDto get() {
        return CompanyProfileDto.from(getSingleton());
    }

    public CompanyProfileDto update(CompanyProfileUpdateRequest req, UUID userId) {
        CompanyProfile p = getSingleton();
        p.setCompanyName(req.companyName());
        p.setRegisteredAddress(req.registeredAddress());
        p.setOperatingAddress(req.operatingAddress());
        p.setPhone(req.phone());
        p.setEmail(req.email());
        p.setWebsite(req.website());
        p.setTaxId(req.taxId());
        p.setVatRegistrationNumber(req.vatRegistrationNumber());
        p.setVatRatePct(req.vatRatePct() != null ? req.vatRatePct() : BigDecimal.ZERO);
        p.setBankName(req.bankName());
        p.setBankAccountName(req.bankAccountName());
        p.setBankAccountNumber(req.bankAccountNumber());
        p.setBankBranch(req.bankBranch());
        p.setBankSwiftCode(req.bankSwiftCode());
        p.setUpdatedBy(userId);
        return CompanyProfileDto.from(companyProfileRepo.save(p));
    }

    public CompanyProfileDto uploadLogo(MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("No file was uploaded");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("Logo must be an image file");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new BusinessException("Logo must be 2MB or smaller");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException("Failed to read uploaded file: " + e.getMessage());
        }

        storage.upload(LOGO_OBJECT_PATH, bytes, contentType);

        CompanyProfile p = getSingleton();
        p.setLogoObjectPath(LOGO_OBJECT_PATH);
        p.setLogoContentType(contentType);
        p.setUpdatedBy(userId);
        return CompanyProfileDto.from(companyProfileRepo.save(p));
    }

    @Transactional(readOnly = true)
    public byte[] getLogoBytes() {
        CompanyProfile p = getSingleton();
        if (p.getLogoObjectPath() == null) {
            throw new BusinessException("No logo has been uploaded");
        }
        return storage.download(p.getLogoObjectPath());
    }

    @Transactional(readOnly = true)
    public String getLogoContentType() {
        return getSingleton().getLogoContentType();
    }

    private CompanyProfile getSingleton() {
        return companyProfileRepo.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> companyProfileRepo.save(
                        CompanyProfile.builder().companyName("My Company").build()));
    }
}
