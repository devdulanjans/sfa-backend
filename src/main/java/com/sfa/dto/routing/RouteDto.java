package com.sfa.dto.routing;

import java.util.List;

/**
 * A road route, already decoded — {@code points} is a list of [latitude, longitude] pairs
 * so clients can draw it without knowing Google's encoded-polyline format. {@code steps} are the
 * turn-by-turn instructions, in order, for in-app navigation.
 */
public record RouteDto(
        double distanceMeters,
        double durationSeconds,
        List<double[]> points,
        List<Step> steps
) {
    /**
     * One manoeuvre. {@code maneuver} is Google's code (TURN_LEFT, TURN_SLIGHT_RIGHT, ROUNDABOUT_LEFT,
     * UTURN_RIGHT, STRAIGHT, MERGE, FORK_LEFT, RAMP_RIGHT, NAME_CHANGE, DEPART, …; may be null);
     * {@code instruction} is the human text ("Turn left onto Galle Rd"). The step starts at
     * start* and ends where the NEXT manoeuvre happens (end*).
     */
    public record Step(
            double distanceMeters,
            double durationSeconds,
            String instruction,
            String maneuver,
            double startLat,
            double startLng,
            double endLat,
            double endLng
    ) {}
}
