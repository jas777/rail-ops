package com.jas777.railops.model;

/**
 * Reprezentuje możliwe połączenie z jednego segmentu torowego do drugiego.
 */
public record TrackLink(String targetTrackId, String requiredSwitchId, String requiredSwitchState) {
    public TrackLink(String targetTrackId) {
        this(targetTrackId, null, null);
    }

    public boolean isConditional() {
        return requiredSwitchId != null;
    }
}