package com.jas777.railops.model;

import java.util.ArrayList;
import java.util.List;

public class StationConfig {
    private String stationName;
    private List<Track> tracks;
    private List<Switch> switches;
    private List<Waypoint> waypoints;
    private List<Signal> signals;

    public StationConfig() {
        this.waypoints = new ArrayList<>();
        this.signals = new ArrayList<>();
    }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public List<Track> getTracks() { return tracks; }
    public void setTracks(List<Track> tracks) { this.tracks = tracks; }

    public List<Switch> getSwitches() { return switches; }
    public void setSwitches(List<Switch> switches) { this.switches = switches; }

    public List<Waypoint> getWaypoints() { return waypoints; }
    public void setWaypoints(List<Waypoint> waypoints) { this.waypoints = waypoints; }

    public List<Signal> getSignals() { return signals; }
    public void setSignals(List<Signal> signals) { this.signals = signals; }
}