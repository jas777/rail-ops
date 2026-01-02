package com.jas777.railops.logic;

import com.jas777.railops.model.*;
import javafx.animation.AnimationTimer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class SimulationController {

    private final ObjectProperty<LocalTime> currentTime = new SimpleObjectProperty<>(LocalTime.of(6, 0));
    private final List<TimetableEntry> timetable;
    private final List<Train> activeTrains;
    private final Set<String> occupiedNodes;
    private final Map<String, String> platformAssignments; // platform -> trainNumber
    private final StationConfig config;
    private final Map<String, List<TrackLink>> logicalGraph;
    private final PathFinder pathFinder;

    private AnimationTimer simulationTimer;
    private long lastUpdate = 0;
    private int timeAcceleration = 60; // 1 real second = 60 simulated seconds

    public SimulationController(StationConfig config, Map<String, List<TrackLink>> logicalGraph) {
        this.config = config;
        this.logicalGraph = logicalGraph;
        this.timetable = new ArrayList<>();
        this.activeTrains = new ArrayList<>();
        this.occupiedNodes = new HashSet<>();
        this.platformAssignments = new HashMap<>();
        this.pathFinder = new PathFinder(logicalGraph);

        loadTimetable();
    }

    private void loadTimetable() {
        // TODO: Load from file
        timetable.add(new TimetableEntry("IC 1001", "Warsaw", "Krakow",
                LocalTime.of(6, 15), LocalTime.of(6, 20), "1", "T1-START"));
        timetable.add(new TimetableEntry("IC 1002", "Gdansk", "Wroclaw",
                LocalTime.of(6, 30), LocalTime.of(6, 35), "2", "T2-START"));
        timetable.add(new TimetableEntry("IC 1003", "Poznan", "Lublin",
                LocalTime.of(6, 45), LocalTime.of(6, 50), "1", "T1-START"));
        timetable.add(new TimetableEntry("IC 1004", "Lodz", "Katowice",
                LocalTime.of(7, 0), LocalTime.of(7, 5), "3", "T3-START"));
    }

    public void start() {
        simulationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }

                long elapsed = now - lastUpdate;
                if (elapsed >= 1_000_000_000 / 60) { // 60 FPS
                    updateSimulation();
                    lastUpdate = now;
                }
            }
        };
        simulationTimer.start();
    }

    public void stop() {
        if (simulationTimer != null) {
            simulationTimer.stop();
        }
    }

    private void updateSimulation() {
        // Advance time
        currentTime.set(currentTime.get().plusSeconds(timeAcceleration / 60));

        // Check for new train spawns
        spawnScheduledTrains();

        // Update active trains
        updateActiveTrains();
    }

    private void spawnScheduledTrains() {
        LocalTime now = currentTime.get();

        for (TimetableEntry entry : timetable) {
            // Check if train should spawn (5 minutes before scheduled arrival)
            LocalTime spawnTime = entry.scheduledArrival().minusMinutes(5);

            if (now.isAfter(spawnTime) && now.isBefore(entry.scheduledArrival().plusMinutes(10))) {
                // Check if train already spawned
                boolean alreadySpawned = activeTrains.stream()
                        .anyMatch(t -> t.getTrainNumber().equals(entry.trainNumber()));

                if (!alreadySpawned) {
                    attemptSpawnTrain(entry);
                }
            }
        }
    }

    private void attemptSpawnTrain(TimetableEntry entry) {
        String entryNode = entry.entryNodeId();

        // Check if entry is free
        if (occupiedNodes.contains(entryNode)) {
            // Entry blocked, train will be delayed
            return;
        }

        // Find available platform
        String targetPlatform = findAvailablePlatform(entry.designatedPlatform());
        if (targetPlatform == null) {
            return; // No platform available
        }

        // Create train
        Train train = new Train(
                entry.trainNumber(),
                entry.fromStation(),
                entry.toStation(),
                entry.scheduledArrival(),
                entry.scheduledDeparture(),
                entry.designatedPlatform()
        );

        train.setCurrentPlatform(targetPlatform);
        train.setStatus(Train.TrainStatus.ENTERING);
        train.setCurrentNodeId(entryNode);

        // Find path to platform
        String platformNodeId = "PLATFORM-" + targetPlatform;
        List<String> path = pathFinder.findPath(entryNode, platformNodeId, new HashMap<>());

        if (path != null && !path.isEmpty()) {
            train.setCurrentPath(path);
            occupiedNodes.addAll(path);
            platformAssignments.put(targetPlatform, train.getTrainNumber());
            activeTrains.add(train);
        }
    }

    private String findAvailablePlatform(String preferred) {
        // Try preferred platform first
        if (!platformAssignments.containsKey(preferred)) {
            return preferred;
        }

        // Find any available platform
        // TODO: Pull platform from station config
        List<String> platforms = Arrays.asList("1", "2", "3", "4");
        for (String platform : platforms) {
            if (!platformAssignments.containsKey(platform)) {
                return platform;
            }
        }

        return null; // All platforms occupied
    }

    private void updateActiveTrains() {
        LocalTime now = currentTime.get();
        List<Train> trainsToRemove = new ArrayList<>();

        for (Train train : activeTrains) {
            switch (train.getStatus()) {
                case ENTERING -> {
                    // Simulate train movement to platform
                    if (train.getCurrentPath() != null && !train.getCurrentPath().isEmpty()) {
                        train.setStatus(Train.TrainStatus.ARRIVING);
                    }
                }
                case ARRIVING -> {
                    // Train reaches platform
                    train.setActualArrival(now);
                    train.setStatus(Train.TrainStatus.AT_PLATFORM);
                }
                case AT_PLATFORM -> {
                    // Check if it's time to depart
                    if (now.isAfter(train.getScheduledDeparture())) {
                        train.setActualDeparture(now);
                        train.setStatus(Train.TrainStatus.DEPARTING);
                    }
                }
                case DEPARTING -> {
                    // Clear occupied nodes and remove train
                    occupiedNodes.removeAll(train.getCurrentPath());
                    platformAssignments.remove(train.getCurrentPlatform());
                    train.setStatus(Train.TrainStatus.DEPARTED);
                    trainsToRemove.add(train);
                }
            }
        }

        activeTrains.removeAll(trainsToRemove);
    }

    // Getters
    public ObjectProperty<LocalTime> currentTimeProperty() { return currentTime; }
    public LocalTime getCurrentTime() { return currentTime.get(); }
    public List<Train> getActiveTrains() { return new ArrayList<>(activeTrains); }
    public Set<String> getOccupiedNodes() { return new HashSet<>(occupiedNodes); }
    public List<TimetableEntry> getTimetable() { return new ArrayList<>(timetable); }
    public void setTimeAcceleration(int acceleration) { this.timeAcceleration = acceleration; }
}