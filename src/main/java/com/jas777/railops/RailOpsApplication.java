package com.jas777.railops;

import com.jas777.railops.views.StationView;
import com.jas777.railops.views.TimetableView;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.animation.AnimationTimer;

public class RailOpsApplication extends Application {

    private StationView stationView;
    private TimetableView timetableView;
    private BorderPane mainLayout;
    private HBox navbar;
    private Button stationButton;
    private Button timetableButton;
    private Label speedLabel;
    private AnimationTimer refreshTimer;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("RailOps - Railway Station Controller");

        mainLayout = new BorderPane();

        // Create navigation bar
        createNavbar();
        mainLayout.setTop(navbar);

        // Create views
        stationView = new StationView();
        timetableView = new TimetableView(stationView.getSimulationController());

        // Start with station view
        mainLayout.setCenter(stationView);
        updateNavbarSelection(true);

        // Setup periodic refresh for timetable
        refreshTimer = new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 500_000_000) { // Update every 0.5 seconds
                    if (mainLayout.getCenter() == timetableView) {
                        timetableView.refresh();
                    }
                    lastUpdate = now;
                }
            }
        };
        refreshTimer.start();

        Scene scene = new Scene(mainLayout, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            stationView.cleanup();
            refreshTimer.stop();
        });
    }

    private void createNavbar() {
        navbar = new HBox(10);
        navbar.setPadding(new Insets(10));
        navbar.setAlignment(Pos.CENTER_LEFT);
        navbar.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #555; -fx-border-width: 0 0 2 0;");

        // Station View Button
        stationButton = new Button("Station View");
        stationButton.setOnAction(e -> switchToStationView());
        styleNavButton(stationButton);

        // Timetable Button
        timetableButton = new Button("Timetable");
        timetableButton.setOnAction(e -> switchToTimetableView());
        styleNavButton(timetableButton);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Simulation speed control
        Label speedLabelText = new Label("Speed:");
        speedLabelText.setTextFill(Color.WHITE);

        Slider speedSlider = new Slider(1, 120, 60);
        speedSlider.setShowTickLabels(false);
        speedSlider.setShowTickMarks(false);
        speedSlider.setMajorTickUnit(30);
        speedSlider.setMinorTickCount(5);
        speedSlider.setPrefWidth(150);

        speedLabel = new Label("60x");
        speedLabel.setTextFill(Color.YELLOW);
        speedLabel.setMinWidth(50);

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int speed = newVal.intValue();
            speedLabel.setText(speed + "x");
            if (stationView != null && stationView.getSimulationController() != null) {
                stationView.getSimulationController().setTimeAcceleration(speed);
            }
        });

        HBox speedControl = new HBox(5, speedLabelText, speedSlider, speedLabel);
        speedControl.setAlignment(Pos.CENTER);

        navbar.getChildren().addAll(stationButton, timetableButton, spacer, speedControl);
    }

    private void styleNavButton(Button button) {
        button.setStyle("-fx-background-color: #444; -fx-text-fill: white; " +
                "-fx-padding: 8 15; -fx-cursor: hand; -fx-font-size: 14px;");

        button.setOnMouseEntered(e -> {
            if (!button.getStyle().contains("background-color: #666")) {
                button.setStyle("-fx-background-color: #555; -fx-text-fill: white; " +
                        "-fx-padding: 8 15; -fx-cursor: hand; -fx-font-size: 14px;");
            }
        });

        button.setOnMouseExited(e -> {
            if (!button.getStyle().contains("background-color: #666")) {
                button.setStyle("-fx-background-color: #444; -fx-text-fill: white; " +
                        "-fx-padding: 8 15; -fx-cursor: hand; -fx-font-size: 14px;");
            }
        });
    }

    private void updateNavbarSelection(boolean isStationView) {
        if (isStationView) {
            stationButton.setStyle("-fx-background-color: #666; -fx-text-fill: yellow; " +
                    "-fx-padding: 8 15; -fx-cursor: hand; -fx-font-size: 14px; " +
                    "-fx-font-weight: bold;");
            timetableButton.setStyle("-fx-background-color: #444; -fx-text-fill: white; " +
                    "-fx-padding: 8 15; -fx-cursor: hand; -fx-font-size: 14px;");
        } else {
            timetableButton.setStyle("-fx-background-color: #666; -fx-text-fill: yellow; " +
                    "-fx-padding: 8 15; -fx-cursor: hand; -fx-font-size: 14px; " +
                    "-fx-font-weight: bold;");
            stationButton.setStyle("-fx-background-color: #444; -fx-text-fill: white; " +
                    "-fx-padding: 8 15; -fx-cursor: hand; -fx-font-size: 14px;");
        }
    }

    private void switchToStationView() {
        mainLayout.setCenter(stationView);
        updateNavbarSelection(true);
    }

    private void switchToTimetableView() {
        mainLayout.setCenter(timetableView);
        updateNavbarSelection(false);
        timetableView.refresh();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
