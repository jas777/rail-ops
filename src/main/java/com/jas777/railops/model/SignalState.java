package com.jas777.railops.model;

public class SignalState {
    private final String id;
    private Signal.SignalAspect aspect;

    public SignalState(String id, Signal.SignalAspect defaultAspect) {
        this.id = id;
        this.aspect = defaultAspect;
    }

    public String getId() {
        return id;
    }

    public Signal.SignalAspect getAspect() {
        return aspect;
    }

    public void setAspect(Signal.SignalAspect aspect) {
        this.aspect = aspect;
    }
}