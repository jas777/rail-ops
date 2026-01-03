package com.jas777.railops.views;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jas777.railops.RailOpsApplication;
import com.jas777.railops.model.*;
import com.jas777.railops.logic.LogicalGraphBuilder;
import com.jas777.railops.logic.SimulationController;

import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.geometry.Bounds;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;

record Coords(double x, double y) {}

public class StationView extends Pane {

    private final double TRACK_WIDTH = 3.0;
    private final double TEXT_SIZE = 10.0;
    private final double NODE_RADIUS = 4.0;
    private final double SWITCH_CLICK_RADIUS = 15.0;
    private final double WAYPOINT_RADIUS = 6.0;
    private final double SIGNAL_SIZE = 8.0;

    private double scaleValue = 1.0;
    private double translateX = 0.0;
    private double translateY = 0.0;
    private final double ZOOM_FACTOR = 1.1;
    private final double MIN_SCALE = 0.1;
    private final double MAX_SCALE = 5.0;
    private final double VISUAL_PADDING = 25.0;

    private double initialMouseX;
    private double initialMouseY;

    private final Group trackGroup = new Group();
    private final Group switchGroup = new Group();
    private final Group signalGroup = new Group();
    private final Group waypointGroup = new Group();
    private final Group schematicGroup = new Group();

    private Text stationNameText;
    private Text clockText;
    private StationConfig config;
    private Map<String, SwitchState> switchStates = new HashMap<>();
    private Map<String, SignalState> signalStates = new HashMap<>();
    private Map<String, List<TrackLink>> logicalGraphMap = new HashMap<>();
    private final Map<String, Coords> nodePositions = new HashMap<>();
    private final Map<String, Switch> switchMap = new HashMap<>();
    private final Map<String, Track> nodeToTrackMap = new HashMap<>();

    private SimulationController simulationController;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ContextMenu switchContextMenu;
    private ContextMenu signalContextMenu;
    private String selectedSwitchId;
    private String selectedSignalId;

    public StationView() {
        this.setFocusTraversable(true);
        this.getStyleClass().add("station-view");

        // Organize drawing layers
        Group drawingGroup = new Group();
        drawingGroup.getChildren().addAll(trackGroup, switchGroup, waypointGroup, signalGroup);

        schematicGroup.getChildren().add(drawingGroup);
        this.getChildren().add(schematicGroup);

        try {
            config = loadConfig("station_config.json");

            initializeStationLogic(config);
            mapNodesToPositions(config);

            simulationController = new SimulationController(config, logicalGraphMap, switchStates);

            clockText = new Text();
            clockText.setFont(Font.font("Arial", 18));
            clockText.setFill(Color.YELLOW);
            this.getChildren().add(clockText);

            simulationController.currentTimeProperty().addListener((obs, old, newTime) -> {
                clockText.setText(newTime.format(timeFormatter));
                redrawTracks();
                drawStation(config);
            });

            drawStation(config);

            this.layout();

            this.widthProperty().addListener((obs, oldVal, newVal) -> {
                centerTextPosition(newVal.doubleValue());
                positionClock(newVal.doubleValue());
                centerView();
            });
            this.heightProperty().addListener((obs, oldVal, newVal) -> {
                centerView();
            });

            this.setOnScroll(this::handleZoom);
            this.setOnMousePressed(this::handleMousePressed);
            this.setOnMouseDragged(this::handleMouseDragged);

            this.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.SPACE) {
                    resetView();
                    event.consume();
                }
            });

            centerTextPosition(this.getWidth());
            positionClock(this.getWidth());
            centerView();

            simulationController.start();

        } catch (IOException e) {
            System.err.println("Error loading station configuration: " + e.getMessage());
            e.printStackTrace();
            Text errorText = new Text(100, 100, "Error: Could not load station configuration.");
            errorText.setFill(Color.RED);
            this.getChildren().add(errorText);
        }
    }

    private StationConfig loadConfig(String filename) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = RailOpsApplication.class.getResourceAsStream(filename);
        if (is == null) {
            throw new IOException("Resource file not found: " + filename);
        }
        return mapper.readValue(is, StationConfig.class);
    }

    private void initializeStationLogic(StationConfig config) {
        // Initialize switches
        for (Switch sw : config.getSwitches()) {
            switchStates.put(sw.id(), new SwitchState(sw.id(), sw.defaultState()));
            switchMap.put(sw.id(), sw);
        }

        // Initialize signals
        if (config.getSignals() != null) {
            for (Signal signal : config.getSignals()) {
                signalStates.put(signal.id(), new SignalState(signal.id(), signal.currentAspect()));
            }
        }

        LogicalGraphBuilder builder = new LogicalGraphBuilder();
        this.logicalGraphMap = builder.buildLogicalGraph(config);

        System.out.println("=== Logical Graph ===");
        for (Map.Entry<String, List<TrackLink>> entry : logicalGraphMap.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }

    private void mapNodesToPositions(StationConfig config) {
        nodePositions.clear();
        nodeToTrackMap.clear();

        // Map switch positions using their connection IDs directly
        for (Switch sw : config.getSwitches()) {
            if (sw.p1() != null && sw.p1().size() >= 2) {
                String p1ConnId = sw.p1ConnectionId();
                if (p1ConnId != null) {
                    nodePositions.put(p1ConnId, new Coords(sw.p1().get(0), sw.p1().get(1)));
                }
            }

            if (sw.p2MainConnectionId() != null && sw.getP2Main() != null && sw.getP2Main().size() >= 2) {
                nodePositions.put(sw.p2MainConnectionId(),
                        new Coords(sw.getP2Main().get(0), sw.getP2Main().get(1)));
            }

            if (sw.p2SideConnectionId() != null && sw.getP2Side() != null && sw.getP2Side().size() >= 2) {
                nodePositions.put(sw.p2SideConnectionId(),
                        new Coords(sw.getP2Side().get(0), sw.getP2Side().get(1)));
            }
        }

        // Map track entry and exit nodes and track associations
        for (Track track : config.getTracks()) {
            List<List<Double>> points = track.points();
            if (!points.isEmpty()) {
                List<Double> firstPoint = points.get(0);
                List<Double> lastPoint = points.get(points.size() - 1);

                if (track.entryNodeId() != null) {
                    if (firstPoint.size() >= 2) {
                        nodePositions.put(track.entryNodeId(),
                                new Coords(firstPoint.get(0), firstPoint.get(1)));
                    }
                    nodeToTrackMap.put(track.entryNodeId(), track);
                }
                if (track.exitNodeId() != null) {
                    if (lastPoint.size() >= 2) {
                        nodePositions.put(track.exitNodeId(),
                                new Coords(lastPoint.get(0), lastPoint.get(1)));
                    }
                    nodeToTrackMap.put(track.exitNodeId(), track);
                }
            }
        }

        System.out.println("=== Node Positions ===");
        for (Map.Entry<String, Coords> entry : nodePositions.entrySet()) {
            System.out.println(entry.getKey() + " at " + entry.getValue());
        }
    }

    private void drawStation(StationConfig config) {
        trackGroup.getChildren().clear();
        switchGroup.getChildren().clear();
        signalGroup.getChildren().clear();
        waypointGroup.getChildren().clear();

//        schematicGroup.setTranslateX(0);
//        schematicGroup.setTranslateY(0);

        if (stationNameText == null) {
            stationNameText = new Text(config.getStationName().toUpperCase());
            stationNameText.setFont(Font.font("Arial", 24));
            stationNameText.setFill(Color.YELLOW);
            stationNameText.setY(50);
            this.getChildren().add(stationNameText);
        } else {
            stationNameText.setText(config.getStationName().toUpperCase());
        }
        centerTextPosition(this.getWidth());

        // Draw tracks first (bottom layer)
        for (Track track : config.getTracks()) {
            drawTrack(track);
        }

        // Draw switches
        for (Switch sw : config.getSwitches()) {
            drawSwitch(sw);
        }

        // Draw signals on top of tracks
        if (config.getSignals() != null) {
            for (Signal signal : config.getSignals()) {
                drawSignal(signal);
            }
        }

        // Draw waypoints
        if (config.getWaypoints() != null) {
            for (Waypoint wp : config.getWaypoints()) {
                drawWaypoint(wp);
            }
        }

        schematicGroup.applyCss();
        schematicGroup.layout();
        Bounds bounds = schematicGroup.getBoundsInLocal();
        double offsetX = bounds.getMinX();
        double offsetY = bounds.getMinY();
//        schematicGroup.setTranslateX(-offsetX);
//        schematicGroup.setTranslateY(-offsetY);
//        translateX = 0.0;
//        translateY = 0.0;
//        applyTransforms();
    }

    private void drawTrack(Track track) {
        List<List<Double>> points = track.points();
        Color color = getColorFromString(track.color());

        // Check if ANY node associated with this track is occupied
        boolean isOccupied = false;
        if (simulationController != null) {
            Set<String> occupied = simulationController.getOccupiedNodes();

            // Check if entry or exit nodes are occupied
            if (track.entryNodeId() != null && occupied.contains(track.entryNodeId())) {
                isOccupied = true;
            }
            if (track.exitNodeId() != null && occupied.contains(track.exitNodeId())) {
                isOccupied = true;
            }

            // Also check by track ID
            if (occupied.contains(track.id())) {
                isOccupied = true;
            }
        }

        // Override color if occupied
        if (isOccupied) {
            color = Color.rgb(200, 0, 0); // Bright red for occupied tracks
        }

        if (points.isEmpty()) return;

        if (points.size() == 1) {
            List<Double> point = points.get(0);
            if (point.size() >= 2) {
                double x = point.get(0);
                double y = point.get(1);

                Coords entryCoords = nodePositions.get(track.entryNodeId());
                Coords exitCoords = nodePositions.get(track.exitNodeId());

                if (entryCoords != null && exitCoords != null) {
                    drawLine(entryCoords.x(), entryCoords.y(), exitCoords.x(), exitCoords.y(), color);
                } else {
                    drawLine(x - 5, y, x + 5, y, color);
                }
            }
        } else {
            for (int i = 0; i < points.size() - 1; i++) {
                List<Double> p1 = points.get(i);
                List<Double> p2 = points.get(i + 1);
                if (p1.size() >= 2 && p2.size() >= 2) {
                    drawLine(p1.get(0), p1.get(1), p2.get(0), p2.get(1), color);
                }
            }
        }
    }

    private void drawSwitch(Switch sw) {
        String swId = sw.id();
        String currentState = switchStates.get(swId).getState();

        Coords p1Coords = nodePositions.get(sw.p1ConnectionId());

        if (p1Coords == null) {
            System.err.println("Warning: No position for switch " + swId + " P1 connection: " + sw.p1ConnectionId());
            return;
        }

        // Draw main leg
        if (sw.p2MainConnectionId() != null) {
            Coords p2MainCoords = nodePositions.get(sw.p2MainConnectionId());
            if (p2MainCoords != null) {
                drawSwitchLeg(p1Coords, p2MainCoords, currentState.equals("MAIN"));
            }
        }

        // Draw side leg
        if (sw.p2SideConnectionId() != null) {
            Coords p2SideCoords = nodePositions.get(sw.p2SideConnectionId());
            if (p2SideCoords != null) {
                drawSwitchLeg(p1Coords, p2SideCoords, currentState.equals("SIDE"));
            }
        }

        addSwitchLabel(swId, p1Coords.x(), p1Coords.y());

        // Add invisible click area
        Circle clickArea = new Circle(p1Coords.x(), p1Coords.y(), SWITCH_CLICK_RADIUS);
        clickArea.setFill(Color.TRANSPARENT);
        clickArea.setStroke(Color.TRANSPARENT);
        clickArea.setUserData("SWITCH:" + swId);

        clickArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                showSwitchMenu(event.getScreenX(), event.getScreenY(), swId);
                event.consume();
            }
        });

        switchGroup.getChildren().add(clickArea);
    }

    private void drawSwitchLeg(Coords p1, Coords p2, boolean isActive) {
        double dx = p2.x() - p1.x();
        double dy = p2.y() - p1.y();
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 0.1) return;

        Color activeColor = Color.LIMEGREEN;
        Color inactiveColor = Color.rgb(180, 180, 0);

        if (isActive) {
            drawLine(p1.x(), p1.y(), p2.x(), p2.y(), activeColor);
        } else {
            // Draw only half from p2 back toward p1
            double halfX = p2.x() - dx * 0.5;
            double halfY = p2.y() - dy * 0.5;
            drawLine(p2.x(), p2.y(), halfX, halfY, inactiveColor);
        }
    }

    private void showSwitchMenu(double screenX, double screenY, String switchId) {
        if (switchContextMenu != null) {
            switchContextMenu.hide();
        }

        switchContextMenu = new ContextMenu();
        selectedSwitchId = switchId;

        MenuItem mainItem = new MenuItem("Set to MAIN (+)");
        mainItem.setOnAction(e -> setSwitchState(switchId, "MAIN"));

        MenuItem sideItem = new MenuItem("Set to SIDE (-)");
        sideItem.setOnAction(e -> setSwitchState(switchId, "SIDE"));

        MenuItem currentItem = new MenuItem("Current: " + switchStates.get(switchId).getState());
        currentItem.setDisable(true);

        switchContextMenu.getItems().addAll(currentItem, new SeparatorMenuItem(), mainItem, sideItem);
        switchContextMenu.show(this, screenX, screenY);
    }

    private void drawWaypoint(Waypoint wp) {
        if (wp.position() == null || wp.position().size() < 2) return;

        double x = wp.position().get(0);
        double y = wp.position().get(1);

        if (wp.type() == Waypoint.WaypointType.ENTRANCE) {
            // Draw bidirectional arrow for entrances
            double arrowSize = 15;
            double arrowWidth = 8;

            // Left arrow
            Polygon leftArrow = new Polygon();
            leftArrow.getPoints().addAll(
                    x - arrowSize, y,                    // tip
                    x - 5.0, y - arrowWidth / 2,         // top base
                    x - 5.0, y + arrowWidth / 2          // bottom base
            );
            leftArrow.setFill(Color.ORANGE);
            leftArrow.setStroke(Color.DARKORANGE);
            leftArrow.setStrokeWidth(1.5);
            leftArrow.setUserData("WAYPOINT");

            // Right arrow
            Polygon rightArrow = new Polygon();
            rightArrow.getPoints().addAll(
                    x + arrowSize, y,                    // tip
                    x + 5.0, y - arrowWidth / 2,         // top base
                    x + 5.0, y + arrowWidth / 2          // bottom base
            );
            rightArrow.setFill(Color.ORANGE);
            rightArrow.setStroke(Color.DARKORANGE);
            rightArrow.setStrokeWidth(1.5);
            rightArrow.setUserData("WAYPOINT");

            // Middle line
            Line midLine = new Line(x - 5, y, x + 5, y);
            midLine.setStroke(Color.ORANGE);
            midLine.setStrokeWidth(2);
            midLine.setUserData("WAYPOINT");

            Text label = new Text(wp.name());
            label.setFill(Color.ORANGE);
            label.setFont(Font.font("Arial", 10));
            label.setX(x - label.getLayoutBounds().getWidth() / 2);
            label.setY(y - 20);
            label.setUserData("WAYPOINT");

            waypointGroup.getChildren().addAll(leftArrow, rightArrow, midLine, label);
        } else {
            // Regular waypoint
            Circle circle = new Circle(x, y, WAYPOINT_RADIUS);
            circle.setFill(Color.CYAN);
            circle.setStroke(Color.DARKBLUE);
            circle.setStrokeWidth(1.5);
            circle.setUserData("WAYPOINT");

            Text label = new Text(wp.name());
            label.setFill(Color.CYAN);
            label.setFont(Font.font("Arial", 10));
            label.setX(x + WAYPOINT_RADIUS + 3);
            label.setY(y + 4);
            label.setUserData("WAYPOINT");

            waypointGroup.getChildren().addAll(circle, label);
        }
    }

    private void drawSignal(Signal signal) {
        if (signal.position() == null || signal.position().size() < 2) return;

        double x = signal.position().get(0);
        double y = signal.position().get(1);

        // Find the protected track to determine direction
        Coords protectedNode = nodePositions.get(signal.protectedNodeId());

        double angle = 0;
        if (protectedNode != null) {
            // Calculate angle pointing toward the protected node
            double dx = protectedNode.x() - x;
            double dy = protectedNode.y() - y;
            angle = Math.atan2(dy, dx);
        }

        // Get current aspect
        SignalState state = signalStates.get(signal.id());
        Signal.SignalAspect aspect = state != null ? state.getAspect() : signal.currentAspect();

        Color aspectColor = switch (aspect) {
            case RED -> Color.RED;
            case GREEN -> Color.LIMEGREEN;
            case YELLOW -> Color.YELLOW;
            case GREEN_YELLOW -> Color.rgb(200, 255, 100);
        };

        // Draw shorter arrowhead pointing toward protected node
        double arrowSize = SIGNAL_SIZE;
        Polygon arrow = new Polygon();

        // Arrow points in direction of angle
        double tipX = x + Math.cos(angle) * arrowSize;
        double tipY = y + Math.sin(angle) * arrowSize;

        double baseAngle1 = angle + Math.PI * 3 / 4;
        double baseAngle2 = angle - Math.PI * 3 / 4;

        double base1X = x + Math.cos(baseAngle1) * arrowSize * 0.4;
        double base1Y = y + Math.sin(baseAngle1) * arrowSize * 0.4;
        double base2X = x + Math.cos(baseAngle2) * arrowSize * 0.4;
        double base2Y = y + Math.sin(baseAngle2) * arrowSize * 0.4;

        arrow.getPoints().addAll(
                tipX, tipY,
                base1X, base1Y,
                base2X, base2Y
        );

        arrow.setFill(aspectColor);
        arrow.setStroke(Color.DARKGRAY);
        arrow.setStrokeWidth(1.5);
        arrow.setUserData("SIGNAL:" + signal.id());

        // Signal name
        Text label = new Text(signal.name());
        label.setFill(Color.LIGHTGRAY);
        label.setFont(Font.font("Arial", 8));
        label.setX(x - 8);
        label.setY(y - 15);
        label.setUserData("SIGNAL_LABEL");

        // Add context menu to arrow
        arrow.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                showSignalMenu(event.getScreenX(), event.getScreenY(), signal.id());
                event.consume();
            }
        });

        signalGroup.getChildren().addAll(arrow, label);
    }

    private void showSignalMenu(double screenX, double screenY, String signalId) {
        if (signalContextMenu != null) {
            signalContextMenu.hide();
        }

        signalContextMenu = new ContextMenu();
        selectedSignalId = signalId;

        SignalState state = signalStates.get(signalId);

        MenuItem currentItem = new MenuItem("Current: " + state.getAspect());
        currentItem.setDisable(true);

        MenuItem redItem = new MenuItem("S1 - RED (Stop)");
        redItem.setOnAction(e -> setSignalAspect(signalId, Signal.SignalAspect.RED));

        MenuItem greenItem = new MenuItem("S2 - GREEN (Go)");
        greenItem.setOnAction(e -> setSignalAspect(signalId, Signal.SignalAspect.GREEN));

        MenuItem yellowItem = new MenuItem("S3 - YELLOW (Next Stop)");
        yellowItem.setOnAction(e -> setSignalAspect(signalId, Signal.SignalAspect.YELLOW));

        MenuItem greenYellowItem = new MenuItem("S5 - GREEN+YELLOW (Caution)");
        greenYellowItem.setOnAction(e -> setSignalAspect(signalId, Signal.SignalAspect.GREEN_YELLOW));

        signalContextMenu.getItems().addAll(
                currentItem,
                new SeparatorMenuItem(),
                redItem, greenItem, yellowItem, greenYellowItem
        );
        signalContextMenu.show(this, screenX, screenY);
    }

    private void setSignalAspect(String signalId, Signal.SignalAspect aspect) {
        SignalState state = signalStates.get(signalId);
        if (state != null) {
            state.setAspect(aspect);

            // Find the signal to get its protected node
            if (config.getSignals() != null) {
                for (Signal signal : config.getSignals()) {
                    if (signal.id().equals(signalId)) {
                        // Update simulation controller's signal state
                        if (simulationController != null) {
                            simulationController.setSignalAspect(signal.protectedNodeId(), aspect);
                        }
                        break;
                    }
                }
            }

            drawStation(config);
            applyTransforms();
        }
    }

    private void redrawTracks() {
        // Only redraw tracks without rebuilding the entire scene
        if (config == null) return;

        trackGroup.getChildren().clear();
        drawStation(config);
        for (Track track : config.getTracks()) {
            drawTrack(track);
        }
    }

    private void updateTrainDisplay() {
        redrawTracks();
    }

    private void centerTextPosition(double currentWidth) {
        if (stationNameText != null && currentWidth > 0) {
            double textWidth = stationNameText.getLayoutBounds().getWidth();
            stationNameText.setX((currentWidth / 2) - (textWidth / 2));
        }
    }

    private void positionClock(double currentWidth) {
        if (clockText != null && currentWidth > 0) {
            clockText.setX(currentWidth - 120);
            clockText.setY(30);
        }
    }

    private void handleZoom(ScrollEvent event) {
        double oldScale = scaleValue;
        if (event.getDeltaY() > 0) {
            scaleValue *= ZOOM_FACTOR;
        } else if (event.getDeltaY() < 0) {
            scaleValue /= ZOOM_FACTOR;
        }
        scaleValue = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scaleValue));

        if (scaleValue != oldScale) {
            applyTransforms();
        }

        event.consume();
    }

    private void handleMousePressed(MouseEvent event) {
        this.requestFocus();

        if (event.isPrimaryButtonDown()) {
            initialMouseX = event.getX();
            initialMouseY = event.getY();
            event.consume();
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (event.isPrimaryButtonDown()) {
            double deltaX = event.getX() - initialMouseX;
            double deltaY = event.getY() - initialMouseY;

            translateX = schematicGroup.getTranslateX() + deltaX;
            translateY = schematicGroup.getTranslateY() + deltaY;

            initialMouseX = event.getX();
            initialMouseY = event.getY();

            applyTransforms();
            event.consume();
        }
    }

    public void resetView() {
        scaleValue = 1.0;
        centerView();
    }

    private void centerView() {
        double currentWidth = this.getWidth();
        double currentHeight = this.getHeight();

        if (currentWidth <= 0 || currentHeight <= 0) return;

        schematicGroup.applyCss();
        schematicGroup.layout();
        Bounds bounds = schematicGroup.getBoundsInLocal();

        double contentWidth = bounds.getWidth();
        double contentHeight = bounds.getHeight();

        double centerOffsetX = (currentWidth / 2.0) - (contentWidth / 2.0 * scaleValue);
        double centerOffsetY = (currentHeight / 2.0) - (contentHeight / 2.0 * scaleValue);

        translateX = centerOffsetX + (VISUAL_PADDING * scaleValue);
        translateY = centerOffsetY + (VISUAL_PADDING * scaleValue);

        applyTransforms();
    }

    private void applyTransforms() {
        schematicGroup.setScaleX(scaleValue);
        schematicGroup.setScaleY(scaleValue);

        schematicGroup.setTranslateX(translateX);
        schematicGroup.setTranslateY(translateY);
    }

    private void drawLine(double startX, double startY, double endX, double endY, Color color) {
        Line line = new Line(startX, startY, endX, endY);
        line.setStroke(color);
        line.setStrokeWidth(TRACK_WIDTH);
        trackGroup.getChildren().add(line);
    }

    private void addSwitchLabel(String switchId, double x, double y) {
        Text text = new Text(switchId);
        text.setFill(Color.LIGHTGRAY);
        text.setFont(Font.font("Arial", TEXT_SIZE));
        text.setTextOrigin(VPos.BOTTOM);
        text.setX(x - text.getLayoutBounds().getWidth() / 2.0);
        text.setY(y - 10);

        switchGroup.getChildren().add(text);
    }

    private Color getColorFromString(String colorStr) {
        if (colorStr == null) return Color.GRAY;
        try {
            return Color.web(colorStr);
        } catch (Exception e) {
            return switch (colorStr.toUpperCase()) {
                case "RED" -> Color.RED;
                case "GREEN" -> Color.GREEN;
                case "BLUE" -> Color.BLUE;
                case "YELLOW" -> Color.YELLOW;
                default -> Color.GRAY;
            };
        }
    }

    public void setSwitchState(String switchId, String newState) {
        SwitchState state = switchStates.get(switchId);
        if (state != null) {
            state.setState(newState.toUpperCase());
            try {
                drawStation(config);
                applyTransforms();
            } catch (Exception e) {
                System.err.println("Error redrawing station: " + e.getMessage());
            }
        }
    }

    public Map<String, List<TrackLink>> getLogicalGraphMap() {
        return logicalGraphMap;
    }

    public SimulationController getSimulationController() {
        return simulationController;
    }

    public void cleanup() {
        if (simulationController != null) {
            simulationController.stop();
        }
    }
}