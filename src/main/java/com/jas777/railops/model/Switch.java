package com.jas777.railops.model;

import java.util.List;

public record Switch(String id, List<Double> p1, List<Double> p2Main, List<Double> p2Side, String defaultState,
                     String p1ConnectionId, String p2MainConnectionId, String p2SideConnectionId) {
}