package com.jas777.railops.model;

import java.util.List;

public class Switch {
    private String id;
    private List<Double> p1;
    private List<Double> p2_main; // Point for the main route
    private List<Double> p2_side; // Point for the divergent route
    private String defaultState;
    private boolean isReversed = false; // Added for visual state demonstration

    // Getters and Setters (ensure they match the JSON fields)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<Double> getP1() { return p1; }
    public void setP1(List<Double> p1) { this.p1 = p1; }

    public List<Double> getP2_main() { return p2_main; }
    public void setP2_main(List<Double> p2_main) { this.p2_main = p2_main; }

    public List<Double> getP2_side() { return p2_side; }
    public void setP2_side(List<Double> p2_side) { this.p2_side = p2_side; }

    public String getDefaultState() { return defaultState; }
    public void setDefaultState(String defaultState) { this.defaultState = defaultState; }

    public boolean isReversed() { return isReversed; }
    public void setReversed(boolean reversed) { isReversed = reversed; }
}