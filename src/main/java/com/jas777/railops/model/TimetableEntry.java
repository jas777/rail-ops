package com.jas777.railops.model;

import java.time.LocalTime;

public record TimetableEntry(
        String trainNumber,
        String fromStation,
        String toStation,
        LocalTime scheduledArrival,
        LocalTime scheduledDeparture,
        String designatedPlatform,
        String entryNodeId
) {
}