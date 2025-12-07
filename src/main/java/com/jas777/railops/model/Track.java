package com.jas777.railops.model;

import java.util.List;

public record Track(String id,
                    String type,
                    List<List<Double>> points,
                    String color,
                    String entryConnectionId,
                    String exitConnectionId) {
}