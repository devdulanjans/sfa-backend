package com.sfa.service;

import com.sfa.dto.customer.ShopMatchDto;
import com.sfa.entity.Customer;
import com.sfa.entity.Shop;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Shared shop identity across businesses (V118). When a business adds a customer, this finds
 * whether the real-world shop is already registered on the platform, so the new customer row
 * links to the existing shop instead of creating a duplicate.
 *
 * <p>Matching, strongest first:
 * <ul>
 *   <li>PHONE — same real phone/WhatsApp number (placeholders like 0000000000 never match);</li>
 *   <li>TAX — same BR/VAT number. Identifies a company, and chain branches share it, so it's
 *       only ever a suggestion;</li>
 *   <li>NEARBY — GPS within {@value #NEARBY_METERS} m and a similar name.</li>
 * </ul>
 * Automatic linking (no explicit choice, e.g. a lead converted from the mobile app) happens
 * only when exactly one shop has that real phone number AND the names are clearly similar.
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    static final int NEARBY_METERS = 60;
    private static final int MAX_MATCHES = 8;
    private static final Set<String> NAME_NOISE = Set.of(
            "the", "and", "store", "stores", "shop", "shops", "pvt", "private", "ltd", "limited",
            "co", "company", "enterprise", "enterprises", "trader", "traders", "trading", "mart",
            "center", "centre", "super", "supermarket");

    private final ShopRepository shopRepository;
    private final CustomerRepository customerRepository;

    // ── Normalisation ───────────────────────────────────────────────────────────

    /** Sri Lankan number → 0XXXXXXXXX, or null for empty / placeholder / malformed numbers. */
    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("[^0-9]", "");
        if (d.matches("94\\d{9}")) d = "0" + d.substring(2);
        else if (d.matches("[1-9]\\d{8}")) d = "0" + d;
        if (!d.matches("0[1-9]\\d{8}") || d.matches("0(\\d)\\1{8}") || d.equals("0123456789")) return null;
        return d;
    }

    public static String normalizeTax(String raw) {
        if (raw == null) return null;
        String t = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return t.length() >= 5 ? t : null;
    }

    /** 0..1 — how alike two shop names are, ignoring case, punctuation and words like "Stores". */
    static double nameSimilarity(String a, String b) {
        String x = nameKey(a), y = nameKey(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        if (x.equals(y)) return 1;
        if (Math.min(x.length(), y.length()) >= 4 && (x.contains(y) || y.contains(x))) return 0.9;
        return 1.0 - (double) levenshtein(x, y) / Math.max(x.length(), y.length());
    }

    private static String nameKey(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String w : name.toLowerCase(Locale.ROOT).replace("&", " and ").split("[^a-z0-9]+")) {
            if (!w.isEmpty() && !NAME_NOISE.contains(w)) sb.append(w);
        }
        return sb.toString();
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    private static int distanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double r = 6_371_000, p1 = Math.toRadians(lat1.doubleValue()), p2 = Math.toRadians(lat2.doubleValue());
        double dp = p2 - p1, dl = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return (int) Math.round(2 * r * Math.asin(Math.sqrt(h)));
    }

    // ── Matching ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ShopMatchDto> findMatches(String name, String phone, String whatsapp, String taxNumber,
                                          BigDecimal latitude, BigDecimal longitude) {
        Map<UUID, Candidate> found = new LinkedHashMap<>();

        Set<String> phones = new LinkedHashSet<>();
        Optional.ofNullable(normalizePhone(phone)).ifPresent(phones::add);
        Optional.ofNullable(normalizePhone(whatsapp)).ifPresent(phones::add);
        if (!phones.isEmpty()) {
            shopRepository.findByAnyPhone(phones, PageRequest.of(0, 20))
                    .forEach(s -> candidate(found, s).reasons.add("PHONE"));
        }
        String tax = normalizeTax(taxNumber);
        if (tax != null) {
            shopRepository.findByTaxNorm(tax, PageRequest.of(0, 20))
                    .forEach(s -> candidate(found, s).reasons.add("TAX"));
        }
        if (latitude != null && longitude != null && name != null && name.trim().length() >= 3) {
            // ~0.0006° ≈ 65 m of latitude; the exact distance is checked below.
            BigDecimal d = new BigDecimal("0.0006");
            for (Shop s : shopRepository.findInBox(latitude.subtract(d), latitude.add(d),
                    longitude.subtract(d), longitude.add(d), PageRequest.of(0, 50))) {
                int meters = distanceMeters(latitude, longitude, s.getLatitude(), s.getLongitude());
                if (meters <= NEARBY_METERS && nameSimilarity(name, s.getName()) >= 0.6) {
                    Candidate c = candidate(found, s);
                    c.reasons.add("NEARBY");
                    c.distance = meters;
                }
            }
        }

        List<ShopMatchDto> result = new ArrayList<>();
        found.values().stream()
                .sorted(Comparator.comparingInt((Candidate c) -> -score(c, name)))
                .limit(MAX_MATCHES)
                .forEach(c -> {
                    Shop s = c.shop;
                    Integer meters = c.distance != null ? c.distance
                            : latitude != null && longitude != null && s.getLatitude() != null && s.getLongitude() != null
                                    ? distanceMeters(latitude, longitude, s.getLatitude(), s.getLongitude()) : null;
                    Customer mine = customerRepository.findVisibleByShopId(s.getId()).stream().findFirst().orElse(null);
                    result.add(new ShopMatchDto(s.getId(), s.getName(), s.getPhone(), s.getAddress(), s.getArea(),
                            s.getLatitude(), s.getLongitude(), meters, List.copyOf(c.reasons),
                            mine != null ? mine.getId() : null, mine != null ? mine.getCustomerCode() : null));
                });
        return result;
    }

    private static int score(Candidate c, String name) {
        int s = 0;
        if (c.reasons.contains("PHONE")) s += 300;
        if (c.reasons.contains("NEARBY")) s += 200;
        if (c.reasons.contains("TAX")) s += 100;
        return s + (int) Math.round(nameSimilarity(name, c.shop.getName()) * 99);
    }

    private static Candidate candidate(Map<UUID, Candidate> found, Shop s) {
        return found.computeIfAbsent(s.getId(), k -> new Candidate(s));
    }

    private static final class Candidate {
        final Shop shop;
        final Set<String> reasons = new LinkedHashSet<>();
        Integer distance;
        Candidate(Shop shop) { this.shop = shop; }
    }

    // ── Linking ─────────────────────────────────────────────────────────────────

    /**
     * Sets the shop on a NEW customer row before it's saved: the shop the user explicitly chose,
     * else an unambiguous automatic match, else a brand-new shop from the customer's details.
     */
    @Transactional
    public void attachShopForNewCustomer(Customer c, UUID requestedShopId) {
        if (requestedShopId != null) {
            Shop shop = shopRepository.findById(requestedShopId)
                    .orElseThrow(() -> new ResourceNotFoundException("Shop", requestedShopId));
            customerRepository.findVisibleByShopId(shop.getId()).stream().findFirst().ifPresent(existing -> {
                throw new BusinessException("\"" + shop.getName() + "\" is already your customer (code "
                        + existing.getCustomerCode() + ").");
            });
            fillBlankIdentity(c, shop);
            c.setShop(shop);
            return;
        }
        Shop auto = automaticMatch(c);
        c.setShop(auto != null ? auto : shopRepository.save(newShopFrom(c)));
    }

    /** One shop with this real phone, a clearly similar name, and not already the caller's customer. */
    private Shop automaticMatch(Customer c) {
        Set<String> phones = new LinkedHashSet<>();
        Optional.ofNullable(normalizePhone(c.getPhone())).ifPresent(phones::add);
        Optional.ofNullable(normalizePhone(c.getWhatsappNumber())).ifPresent(phones::add);
        if (phones.isEmpty()) return null;
        List<Shop> byPhone = shopRepository.findByAnyPhone(phones, PageRequest.of(0, 2));
        if (byPhone.size() != 1) return null;
        Shop s = byPhone.get(0);
        if (nameSimilarity(c.getName(), s.getName()) < 0.75) return null;
        if (!customerRepository.findVisibleByShopId(s.getId()).isEmpty()) return null;
        return s;
    }

    /**
     * Keeps the shared profile current while only ONE business uses the shop. Once several
     * businesses share it, one business's edits stay on its own customer row and never
     * rewrite the profile the others matched against.
     */
    @Transactional
    public void syncFromCustomer(Customer c) {
        if (c.getShop() == null) {
            c.setShop(shopRepository.save(newShopFrom(c)));
            return;
        }
        if (customerRepository.countAllChannelsByShopId(c.getShop().getId()) > 1) return;
        Shop s = c.getShop();
        copyIdentity(c, s);
        shopRepository.save(s);
    }

    private static Shop newShopFrom(Customer c) {
        Shop s = new Shop();
        copyIdentity(c, s);
        return s;
    }

    private static void copyIdentity(Customer c, Shop s) {
        s.setName(c.getName());
        s.setPhone(c.getPhone());
        s.setPhoneNorm(normalizePhone(c.getPhone()));
        s.setWhatsappNorm(normalizePhone(c.getWhatsappNumber()));
        s.setEmail(c.getEmail());
        s.setAddress(c.getLocation());
        s.setArea(c.getPlaceOfSupplier());
        s.setTaxNumber(c.getTaxNumber());
        s.setTaxNorm(normalizeTax(c.getTaxNumber()));
        s.setLatitude(c.getLatitude());
        s.setLongitude(c.getLongitude());
    }

    /** Linking to an existing shop: the new row inherits the shared profile where left blank. */
    private static void fillBlankIdentity(Customer c, Shop s) {
        if (isBlank(c.getPhone())) c.setPhone(s.getPhone());
        if (isBlank(c.getEmail())) c.setEmail(s.getEmail());
        if (isBlank(c.getLocation())) c.setLocation(s.getAddress());
        if (isBlank(c.getPlaceOfSupplier())) c.setPlaceOfSupplier(s.getArea());
        if (isBlank(c.getTaxNumber())) c.setTaxNumber(s.getTaxNumber());
        if (c.getLatitude() == null && c.getLongitude() == null) {
            c.setLatitude(s.getLatitude());
            c.setLongitude(s.getLongitude());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
