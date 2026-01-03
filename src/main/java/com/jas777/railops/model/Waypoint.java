package com.jas777.railops.model;

import java.util.List;

/**
 * Represents a waypoint/landmark on the station layout
 */
public record Waypoint(
        String id,
        String name,
        List<Double> position,
        WaypointType type
) {
    public enum WaypointType {
        PLATFORM,
        ENTRANCE,
        EXIT,
        JUNCTION,
        LANDMARK
    }
}