package com.sfa.service;

import com.sfa.dto.CompanyProfileDto;
import com.sfa.dto.CompanyProfileUpdateRequest;
import com.sfa.entity.CompanyProfile;
import com.sfa.entity.Tenant;
import com.sfa.exception.BusinessException;
import com.sfa.repository.CompanyProfileRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.security.TenantContext;
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
    private final TenantRepository         tenantRepo;
    private final MinioStorageService      storage;

    @Transactional(readOnly = true)
    public CompanyProfileDto get() {
        return CompanyProfileDto.from(getSingleton());
    }

    /**
     * Resolves a specific channel's profile explicitly, bypassing the ambient
     * {@link TenantContext} entirely — for invoice printing, where the details that must
     * print are the invoice's OWN order's channel, not whichever channel the printing
     * user's session currently happens to be scoped to (those can differ: a multi-channel
     * admin printing an older invoice after switching channels, a background regenerate, etc).
     */
    @Transactional(readOnly = true)
    public CompanyProfileDto getForTenant(UUID tenantId) {
        return CompanyProfileDto.from(findByTenantOrDefault(tenantId));
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
        p.setRegistrationNumber(req.registrationNumber());
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
        return getLogoBytes(null);
    }

    /**
     * @param tenantId required for the public (unauthenticated) logo endpoint once more than
     *                 one channel exists — TenantContext isn't populated for anonymous
     *                 requests, so the caller must say explicitly whose logo it wants.
     *                 Null falls back to the legacy single-profile lookup.
     */
    @Transactional(readOnly = true)
    public byte[] getLogoBytes(UUID tenantId) {
        CompanyProfile p = tenantId != null ? findByTenantOrDefault(tenantId) : getSingleton();
        if (p.getLogoObjectPath() == null) {
            throw new BusinessException("No logo has been uploaded");
        }
        return storage.download(p.getLogoObjectPath());
    }

    /**
     * Best-effort logo fetch for opportunistic embedding (e.g. invoice PDFs) — swallows any
     * failure and returns null instead of throwing. {@link #getLogoBytes} is @Transactional,
     * so if storage is unreachable, the exception it throws marks the ambient transaction
     * rollback-only the moment it crosses that method's own proxy boundary — a caller
     * catching the exception afterward (e.g. invoice generation falling back to a text-only
     * header) doesn't undo that, and the caller's own transaction later fails to commit with
     * UnexpectedRollbackException. Catching here, inside the transactional method itself,
     * means the exception never escapes this proxy, so nothing gets poisoned.
     */
    @Transactional(readOnly = true)
    public byte[] tryGetLogoBytes() {
        try {
            return getLogoBytes();
        } catch (Exception e) {
            return null;
        }
    }

    /** Tenant-explicit counterpart to {@link #tryGetLogoBytes()} — see {@link #getForTenant}. */
    @Transactional(readOnly = true)
    public byte[] tryGetLogoBytes(UUID tenantId) {
        try {
            return getLogoBytes(tenantId);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public String getLogoContentType() {
        return getLogoContentType(null);
    }

    @Transactional(readOnly = true)
    public String getLogoContentType(UUID tenantId) {
        CompanyProfile p = tenantId != null ? findByTenantOrDefault(tenantId) : getSingleton();
        return p.getLogoContentType();
    }

    /**
     * "The" company profile only means something once a channel is active. SUPER_ADMIN's
     * default platform view is unscoped — without this guard, findFirstByOrderByUpdatedAtDesc()
     * would silently hand back whichever channel's profile was edited most recently, which
     * looks like real data but isn't any specific channel's. Callers must enter a channel
     * first (POST /api/auth/switch-tenant).
     */
    private CompanyProfile getSingleton() {
        if (TenantContext.isUnscoped()) {
            throw new BusinessException("Select a channel first — Company Profile is per-channel.");
        }
        return companyProfileRepo.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> companyProfileRepo.save(
                        CompanyProfile.builder().companyName("My Company").build()));
    }

    /**
     * Falls back to a default profile instead of throwing, same as {@link #getSingleton()} —
     * a channel nobody has visited Settings > Company Profile for yet must not hard-fail
     * invoice/PDF generation. Explicitly loads the named tenant (a real SELECT, not
     * getReferenceById — see TenantAwareEntityListener's comment on why: the caller reads
     * this profile's tenant relation back in the same request to build a response DTO) since,
     * unlike getSingleton(), this can be asked for a channel other than the ambient one.
     */
    private CompanyProfile findByTenantOrDefault(UUID tenantId) {
        return companyProfileRepo.findByTenant_Id(tenantId)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepo.findById(tenantId)
                            .orElseThrow(() -> new BusinessException("Channel not found."));
                    return companyProfileRepo.save(
                            CompanyProfile.builder().tenant(tenant).companyName("My Company").build());
                });
    }
}
