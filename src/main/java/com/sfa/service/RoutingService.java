package com.sfa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfa.dto.routing.RouteDto;
import com.sfa.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin proxy over the Google Routes API (computeRoutes). Keeping the call server-side means the
 * API key is never shipped inside the mobile app, and the provider can be swapped without an
 * app release.
 */
@Slf4j
@Service
public class RoutingService {

    private static final String COMPUTE_ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    // Only request what we use — the field mask also decides which billing SKU applies.
    private static final String FIELD_MASK = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline,"
            + "routes.legs.steps.distanceMeters,routes.legs.steps.staticDuration,"
            + "routes.legs.steps.navigationInstruction,routes.legs.steps.startLocation,routes.legs.steps.endLocation";

    private final String apiKey;
    private final String travelMode;
    private final boolean trafficAware;
    private final RestClient http;

    public RoutingService(@Value("${app.google-routes.api-key:}") String apiKey,
                          @Value("${app.google-routes.travel-mode:DRIVE}") String travelMode,
                          @Value("${app.google-routes.traffic-aware:false}") boolean trafficAware) {
        this.apiKey = apiKey;
        this.travelMode = travelMode;
        this.trafficAware = trafficAware;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public RouteDto route(double fromLat, double fromLng, double toLat, double toLng) {
        if (!isConfigured()) {
            throw new BusinessException("Routing is not configured on the server (GOOGLE_ROUTES_API_KEY)");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("origin", waypoint(fromLat, fromLng));
        body.put("destination", waypoint(toLat, toLng));
        body.put("travelMode", travelMode);
        // routingPreference is only valid for DRIVE / TWO_WHEELER.
        if ("DRIVE".equals(travelMode) || "TWO_WHEELER".equals(travelMode)) {
            body.put("routingPreference", trafficAware ? "TRAFFIC_AWARE" : "TRAFFIC_UNAWARE");
        }
        body.put("polylineEncoding", "ENCODED_POLYLINE");
        body.put("languageCode", "en-US");

        JsonNode resp;
        try {
            resp = http.post()
                    .uri(COMPUTE_ROUTES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("Google Routes API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("Route lookup failed");
        } catch (Exception e) {
            log.warn("Google Routes API unreachable: {}", e.getMessage());
            throw new BusinessException("Route lookup failed");
        }

        JsonNode routes = resp != null ? resp.path("routes") : null;
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            throw new BusinessException("No road route found to this location");
        }
        JsonNode r = routes.get(0);
        return new RouteDto(
                r.path("distanceMeters").asDouble(0),
                parseDuration(r.path("duration").asText("0s")),
                decodePolyline(r.path("polyline").path("encodedPolyline").asText("")),
                steps(r));
    }

    /** Turn-by-turn steps across all legs (one leg for a point-to-point route). */
    private static java.util.List<RouteDto.Step> steps(JsonNode route) {
        java.util.List<RouteDto.Step> out = new java.util.ArrayList<>();
        for (JsonNode leg : route.path("legs")) {
            for (JsonNode st : leg.path("steps")) {
                JsonNode nav = st.path("navigationInstruction");
                JsonNode start = st.path("startLocation").path("latLng");
                JsonNode end = st.path("endLocation").path("latLng");
                out.add(new RouteDto.Step(
                        st.path("distanceMeters").asDouble(0),
                        parseDuration(st.path("staticDuration").asText("0s")),
                        nav.path("instructions").asText(""),
                        nav.hasNonNull("maneuver") ? nav.path("maneuver").asText() : null,
                        start.path("latitude").asDouble(), start.path("longitude").asDouble(),
                        end.path("latitude").asDouble(), end.path("longitude").asDouble()));
            }
        }
        return out;
    }

    private static Map<String, Object> waypoint(double lat, double lng) {
        return Map.of("location", Map.of("latLng", Map.of("latitude", lat, "longitude", lng)));
    }

    /** Google returns durations as e.g. "1480s" (protobuf Duration JSON form). */
    private static double parseDuration(String s) {
        try {
            return Double.parseDouble(s.endsWith("s") ? s.substring(0, s.length() - 1) : s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Standard Google encoded-polyline decoding (precision 1e5). */
    static List<double[]> decodePolyline(String encoded) {
        List<double[]> points = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;
        while (index < encoded.length()) {
            int[] result = new int[1];
            index = decodeValue(encoded, index, result);
            lat += result[0];
            index = decodeValue(encoded, index, result);
            lng += result[0];
            points.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return points;
    }

    private static int decodeValue(String encoded, int index, int[] out) {
        int shift = 0, result = 0, b;
        do {
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < encoded.length());
        out[0] = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return index;
    }
}
