package com.sfa.controller;

import com.sfa.dto.routing.RouteDto;
import com.sfa.exception.BusinessException;
import com.sfa.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routing")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class RoutingController {

    private final RoutingService routingService;

    @GetMapping("/route")
    public RouteDto route(@RequestParam double fromLat, @RequestParam double fromLng,
                          @RequestParam double toLat,   @RequestParam double toLng) {
        if (!valid(fromLat, fromLng) || !valid(toLat, toLng)) {
            throw new BusinessException("Invalid coordinates");
        }
        return routingService.route(fromLat, fromLng, toLat, toLng);
    }

    private static boolean valid(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }
}
