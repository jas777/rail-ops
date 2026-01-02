package com.jas777.railops.views;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jas777.railops.RailOpsApplication;
import com.jas777.railops.model.StationConfig;
import com.jas777.railops.model.Track;
import com.jas777.railops.model.Switch;
import com.jas777.railops.model.SwitchState;
import com.jas777.railops.model.TrackLink;
import com.jas777.railops.logic.LogicalGraphBuilder;
import com.jas777.railops.logic.SimulationController;

import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.geometry.Bounds;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

record Coords(double x, double y) {}

public class StationView extends Pane {

    private final double TRACK_WIDTH = 2.0;
    private final double TEXT_SIZE = 10.0;
    private final double NODE_RADIUS = 4.0;

    private double scaleValue = 1.0;
    private double translateX = 0.0;
    private double translateY = 0.0;
    private final double ZOOM_FACTOR = 1.1;
    private final double MIN_SCALE = 0.1;
    private final double MAX_SCALE = 5.0;
    private final double VISUAL_PADDING = 25.0;

    private double initialMouseX;
    private double initialMouseY;

    private final Group drawingGroup = new Group();
    private final Group schematicGroup = new Group();

    private Text stationNameText;
    private Text clockText;
    private StationConfig config;
    private Map<String, SwitchState> switchStates = new HashMap<>();
    private Map<String, List<TrackLink>> logicalGraphMap = new HashMap<>();
    private final Map<String, Coords> nodePositions = new HashMap<>();

    private SimulationController simulationController;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public StationView() {
        this.setFocusTraversable(true);
        this.getStyleClass().add("station-view");

        schematicGroup.getChildren().add(drawingGroup);
        this.getChildren().add(schematicGroup);

        try {
            config = loadConfig("station_config.json");

            initializeStationLogic(config);
            mapLogicalNodesToPositions(config);

            // Initialize simulation controller
            simulationController = new SimulationController(config, logicalGraphMap);

            // Add clock display
            clockText = new Text();
            clockText.setFont(Font.font("Arial", 18));
            clockText.setFill(Color.YELLOW);
            this.getChildren().add(clockText);

            // Bind clock to simulation time
            simulationController.currentTimeProperty().addListener((obs, old, newTime) -> {
                clockText.setText(newTime.format(timeFormatter));
                updateTrainDisplay();
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

            // Start simulation
            simulationController.start();

        } catch (IOException e) {
            System.err.println("Error loading station configuration: " + e.getMessage());
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
        for (Switch sw : config.getSwitches()) {
            switchStates.put(sw.id(), new SwitchState(sw.id(), sw.defaultState()));
        }

        LogicalGraphBuilder builder = new LogicalGraphBuilder();
        this.logicalGraphMap = builder.buildLogicalGraph(config);
    }

    private void mapLogicalNodesToPositions(StationConfig config) {
        nodePositions.clear();

        for (Switch sw : config.getSwitches()) {
            nodePositions.put(sw.id() + "-P1", new Coords(sw.p1().get(0), sw.p1().get(1)));
            nodePositions.put(sw.id() + "-P2M", new Coords(sw.p2Main().get(0), sw.p2Main().get(1)));
            nodePositions.put(sw.id() + "-P2S", new Coords(sw.p2Side().get(0), sw.p2Side().get(1)));
        }

        for (Track track : config.getTracks()) {
            List<List<Double>> points = track.points();
            if (!points.isEmpty()) {
                nodePositions.put(track.id() + "-START", new Coords(points.getFirst().get(0), points.getFirst().get(1)));
                nodePositions.put(track.id() + "-END", new Coords(points.getLast().get(0), points.getLast().get(1)));
            }
        }
    }

    private void drawStation(StationConfig config) {
        drawingGroup.getChildren().clear();
        schematicGroup.setTranslateX(0);
        schematicGroup.setTranslateY(0);

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

        // Draw tracks
        for (Track track : config.getTracks()) {
            List<List<Double>> points = track.points();
            Color color = Color.web(track.color());
            for (int i = 0; i < points.size() - 1; i++) {
                drawContinuousTrackSegment(points.get(i), points.get(i + 1), color);
            }
        }

        // Draw switches
        for (Switch sw : config.getSwitches()) {
            String swId = sw.id();
            String currentState = switchStates.get(swId).getState();

            String p1Id = swId + "-P1";
            String p2MainId = swId + "-P2M";
            drawSwitchLeg(swId, p1Id, p2MainId, currentState.equals("MAIN"));

            String p2SideId = swId + "-P2S";
            drawSwitchLeg(swId, p1Id, p2SideId, currentState.equals("SIDE"));

            Coords p1Coords = nodePositions.get(p1Id);
            if (p1Coords != null) {
                addSwitchLabel(swId, p1Coords.x(), p1Coords.y());
            }
        }

        // Draw occupied nodes (trains)
        updateTrainDisplay();

        drawingGroup.applyCss();
        drawingGroup.layout();
        Bounds bounds = drawingGroup.getBoundsInLocal();
        double offsetX = bounds.getMinX();
        double offsetY = bounds.getMinY();
        drawingGroup.setTranslateX(-offsetX);
        drawingGroup.setTranslateY(-offsetY);
        translateX = 0.0;
        translateY = 0.0;
        applyTransforms();
    }

    private void updateTrainDisplay() {
        if (simulationController == null) return;

        // Remove old train indicators
        drawingGroup.getChildren().removeIf(node -> node.getUserData() != null &&
                node.getUserData().equals("TRAIN"));

        Set<String> occupiedNodes = simulationController.getOccupiedNodes();

        for (String nodeId : occupiedNodes) {
            Coords coords = nodePositions.get(nodeId);
            if (coords != null) {
                drawTrainIndicator(coords.x(), coords.y());
            }
        }
    }

    private void drawTrainIndicator(double x, double y) {
        Circle indicator = new Circle(x, y, NODE_RADIUS);
        indicator.setFill(Color.RED);
        indicator.setStroke(Color.DARKRED);
        indicator.setStrokeWidth(1.0);
        indicator.setUserData("TRAIN");
        drawingGroup.getChildren().add(indicator);
    }

    private void drawSwitchLeg(String sourceSwitchId, String p1LogicalId, String p2LogicalId, boolean isActive) {
        Coords p1Coords = nodePositions.get(p1LogicalId);
        Coords p2Coords = nodePositions.get(p2LogicalId);

        if (p1Coords == null || p2Coords == null) {
            System.err.println("Błąd: Nie znaleziono współrzędnych dla ID " + p1LogicalId + " lub " + p2LogicalId);
            return;
        }

        double x1 = p1Coords.x();
        double y1 = p1Coords.y();
        double x2 = p2Coords.x();
        double y2 = p2Coords.y();

        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 0.1) return;

        boolean p2IsAnotherSwitch = false;

        if (logicalGraphMap.containsKey(p2LogicalId)) {
            for (TrackLink link : logicalGraphMap.get(p2LogicalId)) {
                String neighborIdBase = link.targetTrackId().split("-")[0];
                if (neighborIdBase.startsWith("S") && !neighborIdBase.equals(sourceSwitchId)) {
                    p2IsAnotherSwitch = true;
                    break;
                }
            }
        }

        double drawFactor = 1.0;
        if (isActive && p2IsAnotherSwitch) {
            drawFactor = 0.5;
        }

        double endX = x1 + dx * drawFactor;
        double endY = y1 + dy * drawFactor;
        Color activeColor = Color.LIMEGREEN;
        Color inactiveColor = Color.YELLOW;

        if (isActive) {
            drawLine(x1, y1, endX, endY, activeColor);
        } else {
            double inactiveEndX = x2 + (x1 - x2) * 0.5;
            double inactiveEndY = y2 + (y1 - y2) * 0.5;
            drawLine(x2, y2, inactiveEndX, inactiveEndY, inactiveColor);
        }
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
        double scaleFactor = scaleValue / oldScale;

        if (scaleFactor != 1.0) {
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

        drawingGroup.applyCss();
        drawingGroup.layout();
        Bounds bounds = drawingGroup.getBoundsInLocal();

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
        drawingGroup.getChildren().add(line);
    }

    private void drawContinuousTrackSegment(List<Double> p1, List<Double> p2, Color color) {
        double x1 = p1.get(0);
        double y1 = p1.get(1);
        double x2 = p2.get(0);
        double y2 = p2.get(1);
        drawLine(x1, y1, x2, y2, color);
    }

    private void addSwitchLabel(String switchId, double x, double y) {
        Text text = new Text(switchId);
        text.setFill(Color.GRAY);
        text.setFont(Font.font("Arial", TEXT_SIZE));
        text.setTextOrigin(VPos.BOTTOM);
        text.setX(x - text.getLayoutBounds().getWidth() / 2.0);
        text.setY(y - 10);

        drawingGroup.getChildren().add(text);
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