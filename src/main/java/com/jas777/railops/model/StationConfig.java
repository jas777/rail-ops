package com.jas777.railops.model;

import java.util.List;

public class StationConfig {
    private String stationName;
    private List<Track> tracks;
    private List<Switch> switches;

    // Getters and Setters (omitted for brevity, assume they exist)
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public List<Track> getTracks() { return tracks; }
    public void setTracks(List<Track> tracks) { this.tracks = tracks; }

    public List<Switch> getSwitches() { return switches; }
    public void setSwitches(List<Switch> switches) { this.switches = switches; }
}