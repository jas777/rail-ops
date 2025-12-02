package com.jas777.railops;

import com.jas777.railops.views.StationView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class RailOpsApplication extends Application {
    @Override
    public void start(Stage stage) {
        // Creates the StationView, which now loads the configuration from JSON.
        StationView stationView = new StationView();

        // Set the stage properties
        Scene scene = new Scene(stationView, 800, 600);
        scene.getStylesheets().add(Objects.requireNonNull(RailOpsApplication.class.getResource("styles.css")).toExternalForm());

        stage.setTitle("RailOps - Railway Traffic Control Simulator");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}