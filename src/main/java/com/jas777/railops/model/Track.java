package com.jas777.railops.model;

import java.util.List;

public class Track {
    private String id;
    private String type;
    private List<List<Double>> points; // List of [X, Y] coordinates
    private String color;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<List<Double>> getPoints() { return points; }
    public void setPoints(List<List<Double>> points) { this.points = points; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}