package com.jas777.railops.views;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jas777.railops.RailOpsApplication;
import com.jas777.railops.model.StationConfig;
import com.jas777.railops.model.Track;
import com.jas777.railops.model.Switch;

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
import java.util.concurrent.atomic.AtomicReference;

public class StationView extends Pane {

    private final double TRACK_WIDTH = 2.0;
    private final double TEXT_SIZE = 10.0;
    private final double STUB_LENGTH = 15.0; // Switch stub length

    // Calculated dimensions for the schematic bounding box
    private double schematicWidth = 0.0;
    private double schematicHeight = 0.0;
    private double minX = Double.MAX_VALUE;
    private double minY = Double.MAX_VALUE;

    private final Group schematicGroup = new Group();
    private Text stationNameText;

    public StationView() {
        this.getStyleClass().add("station-view");
        this.getChildren().add(schematicGroup);

        try {
            StationConfig config = loadConfig("station_config.json");

            // 1. Calculate Bounding Box and Draw Schematic
            calculateBoundingBox(config);
            drawStation(config);

            // 2. Setup Responsiveness (Horizontal and Vertical centering)
            this.widthProperty().addListener((obs, oldVal, newVal) -> {
                recenterSchematic();
            });
            this.heightProperty().addListener((obs, oldVal, newVal) -> {
                recenterSchematic();
            });

            // 3. Initial centering
            recenterSchematic();

        } catch (IOException e) {
            System.err.println("Error loading station configuration: " + e.getMessage());
            Text errorText = new Text(100, 100, "Error: Could not load station configuration. Check if Jackson dependency is present.");
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

    /**
     * Calculates the minimum bounding box that encloses all track and switch points.
     */
    private void calculateBoundingBox(StationConfig config) {
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        // Lambda updateBounds removed. Updating min/max directly in this method.

        // Process tracks
        for (Track track : config.getTracks()) {
            for (List<Double> p : track.getPoints()) {
                double x = p.get(0);
                double y = p.get(1);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        // Process switches
        for (Switch sw : config.getSwitches()) {
            List<List<Double>> points = List.of(sw.getP1(), sw.getP2_main(), sw.getP2_side());
            for (List<Double> p : points) {
                double x = p.get(0);
                double y = p.get(1);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        // Calculate final width and height, plus a small margin (e.g., 5.0)
        double margin = 5.0;
        schematicWidth = (maxX - minX) + 2 * margin;
        schematicHeight = (maxY - minY) + 2 * margin;

        // Adjust minX/minY to include margin for offset calculation
        minX -= margin;
        minY -= margin;

        // If no points were found (shouldn't happen with valid config)
        if (minX == Double.MAX_VALUE) {
            minX = 0; minY = 0; schematicWidth = 100; schematicHeight = 100;
        }
    }


    private void drawStation(StationConfig config) {

        // Map switch tips for quick lookup
        Map<List<Double>, Switch> tipToSwitchMap = createTipToSwitchMap(config);

        // --- Draw Station Name ---
        stationNameText = new Text(config.getStationName().toUpperCase());
        stationNameText.setFont(Font.font("Arial", 24));
        stationNameText.setFill(Color.YELLOW);
        stationNameText.setY(50);
        this.getChildren().add(stationNameText);

        // 1. Draw Tracks (Straight Segments)
        for (Track track : config.getTracks()) {
            List<List<Double>> points = track.getPoints();
            Color color = Color.web(track.getColor());

            for (int i = 0; i < points.size() - 1; i++) {
                List<Double> p1 = points.get(i);
                List<Double> p2 = points.get(i + 1);

                drawContinuousTrackSegment(p1, p2, color);
            }
        }

        // 2. Draw Switches (Junctions and Labels)
        for (Switch sw : config.getSwitches()) {

            String currentState = sw.getDefaultState().toUpperCase();

            // Draw Main Leg
            boolean isMainActive = currentState.equals("MAIN");
            drawSwitchLeg(sw.getP1(), sw.getP2_main(), Color.LIMEGREEN, isMainActive);

            // Draw Side Leg
            boolean isSideActive = currentState.equals("SIDE");
            drawSwitchLeg(sw.getP1(), sw.getP2_side(), Color.YELLOW, isSideActive);

            // Add Switch Label
            addSwitchLabel(sw);
        }
    }

    /**
     * Centers the entire schematic group and the station name text (Horizontal & Vertical).
     */
    private void recenterSchematic() {
        double currentWidth = this.getWidth();
        double currentHeight = this.getHeight();

        if (currentWidth <= 0 || currentHeight <= 0) return;

        // Calculate overall offset for the Pane's center
        double centerOffsetX = (currentWidth / 2.0) - (schematicWidth / 2.0);
        double centerOffsetY = (currentHeight / 2.0) - (schematicHeight / 2.0);

        // Final offset must compensate for:
        // 1. Centering the bounding box: centerOffset
        // 2. Moving the bounding box from its minX/minY origin to (0,0): -minX and -minY

        schematicGroup.setTranslateX(centerOffsetX - minX);
        schematicGroup.setTranslateY(centerOffsetY - minY);

        // Center the station name text (outside the group)
        if (stationNameText != null) {
            double textWidth = stationNameText.getLayoutBounds().getWidth();
            stationNameText.setX((currentWidth / 2) - (textWidth / 2));
        }
    }


    private Map<List<Double>, Switch> createTipToSwitchMap(StationConfig config) {
        Map<List<Double>, Switch> map = new HashMap<>();
        for (Switch sw : config.getSwitches()) {
            map.put(sw.getP2_main(), sw);
            map.put(sw.getP2_side(), sw);
        }
        return map;
    }


    private void drawLine(double startX, double startY, double endX, double endY, Color color) {
        // Draws line to the Group. Coordinates are relative to the Group's origin.
        Line line = new Line(startX, startY, endX, endY);
        line.setStroke(color);
        line.setStrokeWidth(TRACK_WIDTH);
        schematicGroup.getChildren().add(line);
    }

    /**
     * Draws a full track segment.
     */
    private void drawContinuousTrackSegment(List<Double> p1, List<Double> p2, Color color) {
        double x1 = p1.get(0);
        double y1 = p1.get(1);
        double x2 = p2.get(0);
        double y2 = p2.get(1);

        drawLine(x1, y1, x2, y2, color);
    }


    /**
     * Draws the switch leg. If active, draws full line. If inactive, draws track stub (half length).
     */
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
            drawLine(x1, y1, x2, y2, color); // Draw full line
        } else {
            // 1. Switch Stub (Gray, fixed length) - Draw from heel (p1) toward tip (p2)
            double stub1X = x1 + dx * (STUB_LENGTH / length);
            double stub1Y = y1 + dy * (STUB_LENGTH / length);
            drawLine(x1, y1, stub1X, stub1Y, Color.GRAY);

            // 2. Track Stub (Color, half length) - Draw from tip (p2) toward heel (p1)
            double dx_rev = x1 - x2;
            double dy_rev = y1 - y2;

            // Final point is the midpoint (p2 + vector * 0.5)
            double finalX = x2 + dx_rev * 0.5;
            double finalY = y2 + dy_rev * 0.5;

            drawLine(x2, y2, finalX, finalY, color);
        }
    }

    private void addSwitchLabel(Switch sw) {
        double x = sw.getP1().get(0);
        double y = sw.getP1().get(1);
        String label = sw.getId();

        Text text = new Text(label);
        text.setFill(Color.GRAY);
        text.setFont(Font.font("Arial", TEXT_SIZE));

        // Position the text (relative to the Group)
        text.setTextOrigin(VPos.BOTTOM);
        text.setX(x - text.getLayoutBounds().getWidth() / 2.0);
        text.setY(y - 10);

        schematicGroup.getChildren().add(text);
    }
}