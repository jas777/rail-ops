package com.jas777.railops.views;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jas777.railops.RailOpsApplication;
import com.jas777.railops.model.StationConfig;
import com.jas777.railops.model.Track;
import com.jas777.railops.model.Switch;
import com.jas777.railops.model.SwitchState;
import com.jas777.railops.model.TrackLink;
import com.jas777.railops.logic.LogicalGraphBuilder;

import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class StationView extends Pane {

    private final double TRACK_WIDTH = 2.0;
    private final double TEXT_SIZE = 10.0;
    private final double STUB_LENGTH = 15.0;

    private double schematicWidth = 0.0;
    private double schematicHeight = 0.0;
    private double minX = Double.MAX_VALUE;
    private double minY = Double.MAX_VALUE;

    private Map<String, SwitchState> switchStates = new HashMap<>();
    private Map<String, List<TrackLink>> logicalGraphMap = new HashMap<>();

    private final Group schematicGroup = new Group();
    private Text stationNameText;

    public StationView() {
        this.getStyleClass().add("station-view");
        this.getChildren().add(schematicGroup);

        try {
            StationConfig config = loadConfig("station_config.json");

            initializeStationLogic(config);

            calculateBoundingBox(config);
            drawStation(config);

            this.widthProperty().addListener((obs, oldVal, newVal) -> {
                recenterSchematic();
            });
            this.heightProperty().addListener((obs, oldVal, newVal) -> {
                recenterSchematic();
            });

            recenterSchematic();

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

        System.out.println("Logical Graph Built: " + this.logicalGraphMap.size() + " tracks linked.");
    }

    private void calculateBoundingBox(StationConfig config) {
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (Track track : config.getTracks()) {
            for (List<Double> p : track.points()) {
                double x = p.get(0);
                double y = p.get(1);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        for (Switch sw : config.getSwitches()) {
            List<List<Double>> points = List.of(sw.p1(), sw.p2Main(), sw.p2Side());
            for (List<Double> p : points) {
                double x = p.get(0);
                double y = p.get(1);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        double margin = 5.0;
        schematicWidth = (maxX - minX) + 2 * margin;
        schematicHeight = (maxY - minY) + 2 * margin;

        minX -= margin;
        minY -= margin;

        if (minX == Double.MAX_VALUE) {
            minX = 0; minY = 0; schematicWidth = 100; schematicHeight = 100;
        }
    }


    private void drawStation(StationConfig config) {
        schematicGroup.getChildren().clear();

        stationNameText = new Text(config.getStationName().toUpperCase());
        stationNameText.setFont(Font.font("Arial", 24));
        stationNameText.setFill(Color.YELLOW);
        stationNameText.setY(50);

        if (!this.getChildren().contains(stationNameText)) {
            this.getChildren().add(stationNameText);
        }

        for (Track track : config.getTracks()) {
            List<List<Double>> points = track.points();
            Color color = Color.web(track.color());

            for (int i = 0; i < points.size() - 1; i++) {
                List<Double> p1 = points.get(i);
                List<Double> p2 = points.get(i + 1);

                drawContinuousTrackSegment(p1, p2, color);
            }
        }

        for (Switch sw : config.getSwitches()) {
            String currentState = switchStates.get(sw.id()).getState();

            // Draw Main Leg
            boolean isMainActive = currentState.equals("MAIN");
            drawSwitchLeg(sw.p1(), sw.p2Main(), Color.LIMEGREEN, isMainActive);

            // Draw Side Leg
            boolean isSideActive = currentState.equals("SIDE");
            drawSwitchLeg(sw.p1(), sw.p2Side(), Color.YELLOW, isSideActive);

            // Add Switch Label
            addSwitchLabel(sw);
        }
    }

    private void recenterSchematic() {
        double currentWidth = this.getWidth();
        double currentHeight = this.getHeight();

        if (currentWidth <= 0 || currentHeight <= 0) return;

        double centerOffsetX = (currentWidth / 2.0) - (schematicWidth / 2.0);
        double centerOffsetY = (currentHeight / 2.0) - (schematicHeight / 2.0);

        schematicGroup.setTranslateX(centerOffsetX - minX);
        schematicGroup.setTranslateY(centerOffsetY - minY);

        if (stationNameText != null) {
            double textWidth = stationNameText.getLayoutBounds().getWidth();
            stationNameText.setX((currentWidth / 2) - (textWidth / 2));
        }
    }

    private void drawLine(double startX, double startY, double endX, double endY, Color color) {
        Line line = new Line(startX, startY, endX, endY);
        line.setStroke(color);
        line.setStrokeWidth(TRACK_WIDTH);
        schematicGroup.getChildren().add(line);
    }

    private void drawContinuousTrackSegment(List<Double> p1, List<Double> p2, Color color) {
        double x1 = p1.get(0);
        double y1 = p1.get(1);
        double x2 = p2.get(0);
        double y2 = p2.get(1);

        drawLine(x1, y1, x2, y2, color);
    }

    private void drawSwitchLeg(List<Double> p1, List<Double> p2, Color color, boolean isActive) {
        double x1 = p1.get(0);
        double y1 = p1.get(1);
        double x2 = p2.get(0);
        double y2 = p2.get(1);

        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 0.1) return;

        if (isActive) {
            drawLine(x1, y1, x2, y2, color);
        } else {
            double dx_rev = x1 - x2;
            double dy_rev = y1 - y2;

            double finalX = x2 + dx_rev * 0.5;
            double finalY = y2 + dy_rev * 0.5;

            drawLine(x2, y2, finalX, finalY, color);
        }
    }

    private void addSwitchLabel(Switch sw) {
        double x = sw.p1().get(0);
        double y = sw.p1().get(1);
        String label = sw.id();

        Text text = new Text(label);
        text.setFill(Color.GRAY);
        text.setFont(Font.font("Arial", TEXT_SIZE));

        text.setTextOrigin(VPos.BOTTOM);
        text.setX(x - text.getLayoutBounds().getWidth() / 2.0);
        text.setY(y - 10);

        schematicGroup.getChildren().add(text);
    }

    public void setSwitchState(String switchId, String newState) {
        SwitchState state = switchStates.get(switchId);
        if (state != null) {
            state.setState(newState.toUpperCase());
            try {
                // TODO: redraw
            } catch (Exception e) {
                System.err.println("Error redrawing station: " + e.getMessage());
            }
        }
    }

    public Map<String, List<TrackLink>> getLogicalGraphMap() {
        return logicalGraphMap;
    }
}