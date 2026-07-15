package com.sfa.service;

import com.sfa.dto.geo.CheckInRequest;
import com.sfa.entity.Customer;
import com.sfa.entity.CustomerVisit;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.CustomerVisitRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeoService {

    private final CustomerVisitRepository visitRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @Value("${app.geo.geofence-radius-meters:200}")
    private double geofenceRadiusMeters;

    @Transactional
    public CustomerVisit checkIn(CheckInRequest req) {
        UserDetailsImpl principal = currentUser();

        if (visitRepository.findOpenVisit(principal.getId()).isPresent()) {
            throw new BusinessException("Already checked in — please check out first");
        }

        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId()));
        User salesRep = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));

        CustomerVisit visit = new CustomerVisit();
        visit.setSalesRep(salesRep);
        visit.setCustomer(customer);
        visit.setCheckIn(Instant.now());
        visit.setLatitude(req.latitude());
        visit.setLongitude(req.longitude());

        boolean geoFenced = isWithinGeofence(req.latitude(), req.longitude(),
                customer.getLatitude(), customer.getLongitude());
        visit.setGeoFenced(geoFenced);

        return visitRepository.save(visit);
    }

    @Transactional
    public CustomerVisit checkOut(BigDecimal latitude, BigDecimal longitude) {
        UserDetailsImpl principal = currentUser();
        CustomerVisit visit = visitRepository.findOpenVisit(principal.getId())
                .orElseThrow(() -> new BusinessException("No active check-in found"));

        visit.setCheckOut(Instant.now());
        if (latitude != null) visit.setCheckoutLatitude(latitude);
        if (longitude != null) visit.setCheckoutLongitude(longitude);

        return visitRepository.save(visit);
    }

    public Page<CustomerVisit> listVisits(Pageable pageable) {
        UserDetailsImpl principal = currentUser();
        if (principal.getRoleName().equals("SALES_REP")) {
            return visitRepository.findBySalesRepId(principal.getId(), pageable);
        }
        return visitRepository.findAll(pageable);
    }

    private boolean isWithinGeofence(BigDecimal lat, BigDecimal lng,
                                      BigDecimal customerLat, BigDecimal customerLng) {
        if (customerLat == null || customerLng == null) return false;
        double distMeters = haversineMeters(
                lat.doubleValue(), lng.doubleValue(),
                customerLat.doubleValue(), customerLng.doubleValue());
        return distMeters <= geofenceRadiusMeters;
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private UserDetailsImpl currentUser() {
        return (UserDetailsImpl) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
