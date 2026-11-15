package com.sfa.service;

import com.sfa.dto.product.ProductDto;
import com.sfa.entity.Product;
import com.sfa.entity.Tenant;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.ProductRepository;
import com.sfa.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Optional product picture (V123). Uploaded from the admin product form and shown on the public
 * website catalogue only — the sales rep app doesn't fetch it. Stored through
 * {@link MinioStorageService} (MinIO, or the local folder in development).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductImageService {

    private static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    public record Image(byte[] bytes, String contentType) {}

    private final ProductRepository productRepository;
    private final MinioStorageService storage;

    public ProductDto upload(UUID productId, MultipartFile file) {
        Product p = visible(productId);
        if (file == null || file.isEmpty()) throw new BusinessException("Choose an image to upload");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (!TYPES.contains(type)) throw new BusinessException("The picture must be a JPG, PNG, WebP or GIF image");
        if (file.getSize() > MAX_BYTES) throw new BusinessException("The picture must be 3 MB or smaller");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException("Could not read the uploaded file");
        }
        String path = "products/" + p.getId() + "/image";
        storage.upload(path, bytes, type);
        p.setImageObjectPath(path);
        p.setImageContentType(type);
        p.setImageUpdatedAt(Instant.now());
        return ProductDto.from(productRepository.save(p));
    }

    public ProductDto remove(UUID productId) {
        Product p = visible(productId);
        p.setImageObjectPath(null);
        p.setImageContentType(null);
        p.setImageUpdatedAt(null);
        return ProductDto.from(productRepository.save(p));
    }

    /** For the admin form: any product the caller's channel can see. */
    @Transactional(readOnly = true)
    public Image forAdmin(UUID productId) {
        return read(visible(productId));
    }

    /** For the public website: only products the public catalogue lists (active, active business). */
    @Transactional(readOnly = true)
    public Image forPublic(UUID productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (p.getStatus() != Product.ProductStatus.ACTIVE
                || p.getTenant() == null || p.getTenant().getStatus() != Tenant.TenantStatus.ACTIVE) {
            throw new ResourceNotFoundException("Product", productId);
        }
        return read(p);
    }

    private Image read(Product p) {
        if (p.getImageObjectPath() == null) throw new ResourceNotFoundException("Product image", p.getId());
        return new Image(storage.download(p.getImageObjectPath()),
                p.getImageContentType() != null ? p.getImageContentType() : "image/jpeg");
    }

    private Product visible(UUID productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return TenantGuard.requireVisible(p, "Product", productId);
    }
}
