package com.jas777.railops.model;

import java.util.List;

public record Switch(String id,
                     List<Double> p1,
                     List<Double> p2Main,
                     List<Double> p2Side,
                     String defaultState,
                     String p1ConnectionId,
                     String p2MainConnectionId,
                     String p2SideConnectionId) {

    // Helper to get p2Main if it exists, otherwise calculate based on p1 and direction
    public List<Double> getP2Main() {
        if (p2Main != null && !p2Main.isEmpty()) {
            return p2Main;
        }
        // If p2Main not defined, use p1 as fallback
        return p1;
    }

    public List<Double> getP2Side() {
        if (p2Side != null && !p2Side.isEmpty()) {
            return p2Side;
        }
        // If p2Side not defined, use p1 as fallback
        return p1;
    }
}