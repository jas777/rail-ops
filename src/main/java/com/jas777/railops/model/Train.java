package com.jas777.railops.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Train {
    private final String trainNumber;
    private final String fromStation;
    private final String toStation;
    private final LocalTime scheduledArrival;
    private final LocalTime scheduledDeparture;
    private final String designatedPlatform;

    private LocalTime actualArrival;
    private LocalTime actualDeparture;
    private String currentPlatform;
    private TrainStatus status;
    private List<String> currentPath; // List of node IDs the train occupies
    private String currentNodeId;

    public enum TrainStatus {
        SCHEDULED,
        WAITING_ENTRY,
        ENTERING,
        ARRIVING,
        AT_PLATFORM,
        DEPARTING,
        DEPARTED
    }

    public Train(String trainNumber, String fromStation, String toStation,
                 LocalTime scheduledArrival, LocalTime scheduledDeparture,
                 String designatedPlatform) {
        this.trainNumber = trainNumber;
        this.fromStation = fromStation;
        this.toStation = toStation;
        this.scheduledArrival = scheduledArrival;
        this.scheduledDeparture = scheduledDeparture;
        this.designatedPlatform = designatedPlatform;
        this.status = TrainStatus.SCHEDULED;
        this.currentPath = new ArrayList<>();
    }

    // Getters and Setters
    public String getTrainNumber() { return trainNumber; }
    public String getFromStation() { return fromStation; }
    public String getToStation() { return toStation; }
    public LocalTime getScheduledArrival() { return scheduledArrival; }
    public LocalTime getScheduledDeparture() { return scheduledDeparture; }
    public String getDesignatedPlatform() { return designatedPlatform; }

    public LocalTime getActualArrival() { return actualArrival; }
    public void setActualArrival(LocalTime actualArrival) { this.actualArrival = actualArrival; }

    public LocalTime getActualDeparture() { return actualDeparture; }
    public void setActualDeparture(LocalTime actualDeparture) { this.actualDeparture = actualDeparture; }

    public String getCurrentPlatform() { return currentPlatform; }
    public void setCurrentPlatform(String currentPlatform) { this.currentPlatform = currentPlatform; }

    public TrainStatus getStatus() { return status; }
    public void setStatus(TrainStatus status) { this.status = status; }

    public List<String> getCurrentPath() { return currentPath; }
    public void setCurrentPath(List<String> currentPath) { this.currentPath = currentPath; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public int getDelayMinutes() {
        if (actualArrival == null) return 0;
        return (int) java.time.Duration.between(scheduledArrival, actualArrival).toMinutes();
    }
}