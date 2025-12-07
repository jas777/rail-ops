package com.jas777.railops.model;

public class SwitchState {
    private final String id;
    private String state;

    public SwitchState(String id, String defaultState) {
        this.id = id;
        this.state = defaultState.toUpperCase();
    }

    public String getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    public void setState(String newState) {
        if (newState.equalsIgnoreCase("MAIN") || newState.equalsIgnoreCase("SIDE")) {
            this.state = newState.toUpperCase();
        } else {
            throw new IllegalArgumentException("Nieprawidłowy stan zwrotnicy: " + newState);
        }
    }
}