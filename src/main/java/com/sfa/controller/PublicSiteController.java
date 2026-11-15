package com.sfa.controller;

import com.sfa.dto.publicsite.PublicCatalogDto;
import com.sfa.dto.publicsite.PublicPlanDto;
import com.sfa.dto.publicsite.SubmitChannelRequest;
import com.sfa.security.PublicRateLimiter;
import com.sfa.service.ChannelRequestService;
import com.sfa.service.PublicCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Endpoints for the public website — no login. Everything under /api/public is permitAll in
 * SecurityConfig, so every handler here must return only data safe for anonymous visitors.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Public website")
public class PublicSiteController {

    private final PublicCatalogService catalogService;
    private final com.sfa.service.ProductImageService productImageService;
    private final ChannelRequestService channelRequestService;
    private final PublicRateLimiter rateLimiter;
    private final com.sfa.service.AccountSignupService accountSignupService;

    /** Website sign-up: a personal account any business can later invite to a role. */
    public record SignupRequest(
            @jakarta.validation.constraints.NotBlank(message = "Enter your name.")
            @jakarta.validation.constraints.Size(max = 120) String fullName,
            @jakarta.validation.constraints.Email(message = "Enter a valid email address.")
            @jakarta.validation.constraints.Size(max = 120) String email,
            @jakarta.validation.constraints.Size(max = 20) String phone,
            @jakarta.validation.constraints.NotBlank(message = "Choose a password.")
            @jakarta.validation.constraints.Size(min = 8, max = 100, message = "Use at least 8 characters.") String password) {}

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a personal account (no business role until a business invites it)")
    public Map<String, String> signUp(@Valid @RequestBody SignupRequest body, HttpServletRequest request) {
        rateLimiter.check("signup:" + clientIp(request), 5, Duration.ofHours(1));
        return Map.of("username", accountSignupService.signUp(body.fullName(), body.email(), body.phone(), body.password()));
    }

    @GetMapping("/catalog")
    @Operation(summary = "Public product catalogue: active products of active businesses, with promotions")
    public PublicCatalogDto.Page catalog(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String business,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "48") int size) {
        return catalogService.catalog(q, business, page, size);
    }

    @GetMapping("/products/{id}/image")
    @Operation(summary = "A listed product's picture (catalogue links carry ?v= so it can be cached)")
    public org.springframework.http.ResponseEntity<byte[]> productImage(@PathVariable java.util.UUID id) {
        var img = productImageService.forPublic(id);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(img.contentType()))
                .cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(img.bytes());
    }

    @GetMapping("/plans")
    @Operation(summary = "Plans a business can choose when it signs up")
    public List<PublicPlanDto> plans() {
        return channelRequestService.activePlans();
    }

    @PostMapping("/channel-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "\"Create your SFA\": request a new channel; the super admin reviews it")
    public Map<String, String> submitChannelRequest(@Valid @RequestBody SubmitChannelRequest body,
                                                    HttpServletRequest request) {
        String ip = clientIp(request);
        rateLimiter.check("channel-request:" + ip, 5, Duration.ofHours(1));
        return Map.of("referenceNo", channelRequestService.submit(body, ip));
    }

    /** The website proxies /api/*, so the visitor's address arrives in X-Forwarded-For. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }
}
