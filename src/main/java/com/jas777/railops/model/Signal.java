package com.jas777.railops.model;

import java.util.List;

/**
 * Represents a railway signal (Semaphore in Polish railways)
 */
public record Signal(
        String id,
        String name,  // e.g., "Tm1", "Sm2", "Od3"
        List<Double> position,
        SignalAspect currentAspect,
        String protectedNodeId  // The track/switch this signal protects
) {
    public enum SignalAspect {
        RED,              // S1 - Stop
        GREEN,            // S2 - Go
        YELLOW,           // S3 - Next signal stop
        GREEN_YELLOW      // S5 - Proceed with caution, next stop
    }
}