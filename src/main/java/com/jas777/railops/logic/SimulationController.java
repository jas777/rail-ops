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
    private final Map<String, String> platformAssignments;
    private final StationConfig config;
    private final Map<String, List<TrackLink>> logicalGraph;
    private final PathFinder pathFinder;
    private final Map<String, SwitchState> switchStates;
    private final Map<String, SignalState> signalStates;

    private AnimationTimer simulationTimer;
    private long lastUpdate = 0;
    private double timeAcceleration = 60.0;
    private double accumulatedTime = 0.0;

    // Movement simulation
    private static final double MOVEMENT_INTERVAL = 2.0; // seconds between movements
    private double movementTimer = 0.0;

    public SimulationController(StationConfig config, Map<String, List<TrackLink>> logicalGraph,
                                Map<String, SwitchState> switchStates) {
        this.config = config;
        this.logicalGraph = logicalGraph;
        this.switchStates = switchStates;
        this.timetable = new ArrayList<>();
        this.activeTrains = new ArrayList<>();
        this.occupiedNodes = new HashSet<>();
        this.platformAssignments = new HashMap<>();
        this.pathFinder = new PathFinder(logicalGraph);
        this.signalStates = new HashMap<>();

        // Initialize signal states
        if (config.getSignals() != null) {
            for (Signal signal : config.getSignals()) {
                signalStates.put(signal.protectedNodeId(), new SignalState(signal.id(), signal.currentAspect()));
            }
        }

        loadTimetable();
    }

    private void loadTimetable() {
        List<Track> entranceTracks = config.getTracks().stream()
                .filter(t -> t.entryNodeId() != null)
                .filter(t -> "MAIN_LINE".equals(t.type()) || "PLATFORM_TRACK".equals(t.type()))
                .collect(Collectors.toList());

        List<Track> platformTracks = config.getTracks().stream()
                .filter(t -> "PLATFORM_TRACK".equals(t.type()))
                .collect(Collectors.toList());

        System.out.println("=== Timetable Setup ===");
        System.out.println("Entrance tracks: " + entranceTracks.stream().map(Track::id).collect(Collectors.joining(", ")));
        System.out.println("Platform tracks: " + platformTracks.stream().map(Track::id).collect(Collectors.joining(", ")));

        if (!entranceTracks.isEmpty() && !platformTracks.isEmpty()) {
            String firstEntrance = entranceTracks.get(0).entryNodeId();

            timetable.add(new TimetableEntry("IC 1001", "Warsaw", "Krakow",
                    LocalTime.of(6, 15), LocalTime.of(6, 25), "1", firstEntrance));
            timetable.add(new TimetableEntry("IC 1002", "Gdansk", "Wroclaw",
                    LocalTime.of(6, 30), LocalTime.of(6, 40), "2", firstEntrance));

            System.out.println("Created timetable with " + timetable.size() + " trains");
        }
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
                double elapsedSeconds = elapsed / 1_000_000_000.0;

                accumulatedTime += elapsedSeconds * timeAcceleration;

                while (accumulatedTime >= 1.0) {
                    updateSimulation();
                    accumulatedTime -= 1.0;
                }

                lastUpdate = now;
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
        currentTime.set(currentTime.get().plusSeconds(1));
        spawnScheduledTrains();

        movementTimer += 1.0;
        if (movementTimer >= MOVEMENT_INTERVAL) {
            updateActiveTrains();
            movementTimer = 0.0;
        }
    }

    private void spawnScheduledTrains() {
        LocalTime now = currentTime.get();

        for (TimetableEntry entry : timetable) {
            LocalTime spawnTime = entry.scheduledArrival().minusMinutes(5);

            if (now.isAfter(spawnTime) && now.isBefore(entry.scheduledArrival().plusMinutes(10))) {
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

        System.out.println("\n=== Attempting to spawn train " + entry.trainNumber() + " ===");
        System.out.println("Entry node: " + entryNode);

        if (occupiedNodes.contains(entryNode)) {
            System.out.println("  Entry node occupied, delaying spawn");
            return;
        }

        // Check signal at entry
        SignalState entrySignal = signalStates.get(entryNode);
        if (entrySignal != null && entrySignal.getAspect() == Signal.SignalAspect.RED) {
            System.out.println("  Entry signal is RED, waiting");
            return;
        }

        List<Track> platformTracks = config.getTracks().stream()
                .filter(t -> "PLATFORM_TRACK".equals(t.type()))
                .collect(Collectors.toList());

        if (platformTracks.isEmpty()) {
            System.out.println("  No platform tracks found");
            return;
        }

        Track targetPlatform = null;
        for (Track track : platformTracks) {
            String platformNode = track.entryNodeId();
            if (!occupiedNodes.contains(platformNode) &&
                    !platformAssignments.containsValue(track.id())) {
                targetPlatform = track;
                break;
            }
        }

        if (targetPlatform == null) {
            System.out.println("  No available platforms");
            return;
        }

        Train train = new Train(
                entry.trainNumber(),
                entry.fromStation(),
                entry.toStation(),
                entry.scheduledArrival(),
                entry.scheduledDeparture(),
                targetPlatform.id()
        );

        train.setStatus(Train.TrainStatus.WAITING_ENTRY);
        train.setCurrentNodeId(entryNode);

        Map<String, String> currentSwitchStates = new HashMap<>();
        for (Map.Entry<String, SwitchState> e : switchStates.entrySet()) {
            currentSwitchStates.put(e.getKey(), e.getValue().getState());
        }

        String platformNodeId = targetPlatform.entryNodeId();
        List<String> path = pathFinder.findPath(entryNode, platformNodeId, currentSwitchStates);

        if (path != null && !path.isEmpty()) {
            System.out.println("✓ Path found: " + path);
            train.setCurrentPath(new ArrayList<>(path));
            train.setCurrentNodeId(path.get(0));
            platformAssignments.put(targetPlatform.id(), train.getTrainNumber());
            activeTrains.add(train);
            System.out.println("✓ Train ready to enter!");
        } else {
            System.out.println("✗ No path found to platform");
        }
    }

    private void updateActiveTrains() {
        LocalTime now = currentTime.get();
        List<Train> trainsToRemove = new ArrayList<>();

        for (Train train : activeTrains) {
            switch (train.getStatus()) {
                case WAITING_ENTRY -> {
                    // Check if signal permits entry
                    String currentNode = train.getCurrentNodeId();
                    SignalState signal = signalStates.get(currentNode);

                    if (signal == null || signal.getAspect() != Signal.SignalAspect.RED) {
                        // Signal permits, start moving
                        train.setStatus(Train.TrainStatus.ENTERING);
                        occupiedNodes.add(currentNode);
                        System.out.println("Train " + train.getTrainNumber() + " entering at " + currentNode);
                    }
                }
                case ENTERING, ARRIVING -> {
                    // Move train along path
                    moveTrain(train);
                }
                case AT_PLATFORM -> {
                    if (now.isAfter(train.getScheduledDeparture())) {
                        train.setActualDeparture(now);
                        train.setStatus(Train.TrainStatus.DEPARTING);
                        System.out.println("Train " + train.getTrainNumber() + " departing");
                    }
                }
                case DEPARTING -> {
                    // Clear all occupied nodes
                    if (train.getCurrentPath() != null) {
                        occupiedNodes.removeAll(train.getCurrentPath());
                    }
                    occupiedNodes.remove(train.getCurrentNodeId());
                    platformAssignments.values().remove(train.getTrainNumber());
                    train.setStatus(Train.TrainStatus.DEPARTED);
                    trainsToRemove.add(train);
                    System.out.println("Train " + train.getTrainNumber() + " departed");
                }
            }
        }

        activeTrains.removeAll(trainsToRemove);
    }

    private void moveTrain(Train train) {
        List<String> path = train.getCurrentPath();
        if (path == null || path.isEmpty()) return;

        String currentNode = train.getCurrentNodeId();
        int currentIndex = path.indexOf(currentNode);

        if (currentIndex < 0 || currentIndex >= path.size() - 1) {
            // Reached destination
            train.setStatus(Train.TrainStatus.AT_PLATFORM);
            train.setActualArrival(currentTime.get());
            System.out.println("Train " + train.getTrainNumber() + " arrived at platform");
            return;
        }

        // Move to next node
        String nextNode = path.get(currentIndex + 1);

        // Check signal at next node
        SignalState signal = signalStates.get(nextNode);
        if (signal != null && signal.getAspect() == Signal.SignalAspect.RED) {
            System.out.println("Train " + train.getTrainNumber() + " stopped by signal at " + nextNode);
            return;
        }

        // Check if next node is occupied
        if (occupiedNodes.contains(nextNode) && !path.contains(nextNode)) {
            System.out.println("Train " + train.getTrainNumber() + " waiting, next node occupied");
            return;
        }

        // Move train
        occupiedNodes.remove(currentNode);
        occupiedNodes.add(nextNode);
        train.setCurrentNodeId(nextNode);

        System.out.println("Train " + train.getTrainNumber() + " moved: " + currentNode + " -> " + nextNode);

        // Update status
        if (currentIndex >= path.size() - 3) {
            train.setStatus(Train.TrainStatus.ARRIVING);
        }
    }

    public ObjectProperty<LocalTime> currentTimeProperty() { return currentTime; }
    public LocalTime getCurrentTime() { return currentTime.get(); }
    public List<Train> getActiveTrains() { return new ArrayList<>(activeTrains); }
    public Set<String> getOccupiedNodes() { return new HashSet<>(occupiedNodes); }
    public List<TimetableEntry> getTimetable() { return new ArrayList<>(timetable); }
    public void setTimeAcceleration(double acceleration) { this.timeAcceleration = acceleration; }

    public void setSignalAspect(String protectedNodeId, Signal.SignalAspect aspect) {
        SignalState state = signalStates.get(protectedNodeId);
        if (state != null) {
            state.setAspect(aspect);
        }
    }
}