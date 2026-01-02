package com.jas777.railops.views;

import com.jas777.railops.logic.SimulationController;
import com.jas777.railops.model.Train;
import com.jas777.railops.model.TimetableEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TimetableView extends BorderPane {

    private final SimulationController simulationController;
    private final TableView<TimetableRow> table;
    private final ObservableList<TimetableRow> data;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public TimetableView(SimulationController simulationController) {
        this.simulationController = simulationController;
        this.data = FXCollections.observableArrayList();
        this.table = new TableView<>();

        setupTable();
        setupLayout();
        loadTimetableData();
    }

    private void setupTable() {
        // Train Number
        TableColumn<TimetableRow, String> trainCol = new TableColumn<>("Train");
        trainCol.setCellValueFactory(new PropertyValueFactory<>("trainNumber"));
        trainCol.setPrefWidth(80);

        // From
        TableColumn<TimetableRow, String> fromCol = new TableColumn<>("From");
        fromCol.setCellValueFactory(new PropertyValueFactory<>("fromStation"));
        fromCol.setPrefWidth(100);

        // To
        TableColumn<TimetableRow, String> toCol = new TableColumn<>("To");
        toCol.setCellValueFactory(new PropertyValueFactory<>("toStation"));
        toCol.setPrefWidth(100);

        // Scheduled Arrival
        TableColumn<TimetableRow, String> schedArrCol = new TableColumn<>("Sched. Arrival");
        schedArrCol.setCellValueFactory(new PropertyValueFactory<>("scheduledArrival"));
        schedArrCol.setPrefWidth(100);

        // Actual Arrival
        TableColumn<TimetableRow, String> actArrCol = new TableColumn<>("Actual Arrival");
        actArrCol.setCellValueFactory(new PropertyValueFactory<>("actualArrival"));
        actArrCol.setPrefWidth(100);

        // Scheduled Departure
        TableColumn<TimetableRow, String> schedDepCol = new TableColumn<>("Sched. Depart");
        schedDepCol.setCellValueFactory(new PropertyValueFactory<>("scheduledDeparture"));
        schedDepCol.setPrefWidth(100);

        // Actual Departure
        TableColumn<TimetableRow, String> actDepCol = new TableColumn<>("Actual Depart");
        actDepCol.setCellValueFactory(new PropertyValueFactory<>("actualDeparture"));
        actDepCol.setPrefWidth(100);

        // Platform
        TableColumn<TimetableRow, String> platformCol = new TableColumn<>("Platform");
        platformCol.setCellValueFactory(new PropertyValueFactory<>("platform"));
        platformCol.setPrefWidth(80);

        // Delay
        TableColumn<TimetableRow, String> delayCol = new TableColumn<>("Delay");
        delayCol.setCellValueFactory(new PropertyValueFactory<>("delay"));
        delayCol.setPrefWidth(80);
        delayCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("+")) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else if (item.equals("0")) {
                        setStyle("-fx-text-fill: green;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Status
        TableColumn<TimetableRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);

        table.getColumns().addAll(trainCol, fromCol, toCol, schedArrCol, actArrCol,
                schedDepCol, actDepCol, platformCol, delayCol, statusCol);
        table.setItems(data);
    }

    private void setupLayout() {
        Label title = new Label("Station Timetable");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        VBox topBox = new VBox(10, title);
        topBox.setPadding(new Insets(10));

        this.setTop(topBox);
        this.setCenter(table);
        this.setPadding(new Insets(10));
    }

    private void loadTimetableData() {
        for (TimetableEntry entry : simulationController.getTimetable()) {
            data.add(new TimetableRow(entry));
        }
    }

    public void refresh() {
        for (TimetableRow row : data) {
            // Find corresponding active train
            Train train = simulationController.getActiveTrains().stream()
                    .filter(t -> t.getTrainNumber().equals(row.getTrainNumber()))
                    .findFirst()
                    .orElse(null);

            if (train != null) {
                row.updateFromTrain(train);
            }
        }

        table.refresh();
    }

    public static class TimetableRow {
        private String trainNumber;
        private String fromStation;
        private String toStation;
        private String scheduledArrival;
        private String scheduledDeparture;
        private String actualArrival;
        private String actualDeparture;
        private String platform;
        private String delay;
        private String status;

        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        public TimetableRow(TimetableEntry entry) {
            this.trainNumber = entry.trainNumber();
            this.fromStation = entry.fromStation();
            this.toStation = entry.toStation();
            this.scheduledArrival = entry.scheduledArrival().format(formatter);
            this.scheduledDeparture = entry.scheduledDeparture().format(formatter);
            this.actualArrival = "-";
            this.actualDeparture = "-";
            this.platform = entry.designatedPlatform();
            this.delay = "0";
            this.status = "Scheduled";
        }

        public void updateFromTrain(Train train) {
            if (train.getActualArrival() != null) {
                this.actualArrival = train.getActualArrival().format(formatter);

                long delayMinutes = Duration.between(
                        train.getScheduledArrival(),
                        train.getActualArrival()
                ).toMinutes();

                if (delayMinutes > 0) {
                    this.delay = "+" + delayMinutes;
                } else if (delayMinutes < 0) {
                    this.delay = String.valueOf(delayMinutes);
                } else {
                    this.delay = "0";
                }
            }

            if (train.getActualDeparture() != null) {
                this.actualDeparture = train.getActualDeparture().format(formatter);
            }

            if (train.getCurrentPlatform() != null) {
                this.platform = train.getCurrentPlatform();
            }

            this.status = formatStatus(train.getStatus());
        }

        private String formatStatus(Train.TrainStatus status) {
            return switch (status) {
                case SCHEDULED -> "Scheduled";
                case WAITING_ENTRY -> "Waiting";
                case ENTERING -> "Entering";
                case ARRIVING -> "Arriving";
                case AT_PLATFORM -> "At Platform";
                case DEPARTING -> "Departing";
                case DEPARTED -> "Departed";
            };
        }

        // Getters for JavaFX properties
        public String getTrainNumber() { return trainNumber; }
        public String getFromStation() { return fromStation; }
        public String getToStation() { return toStation; }
        public String getScheduledArrival() { return scheduledArrival; }
        public String getScheduledDeparture() { return scheduledDeparture; }
        public String getActualArrival() { return actualArrival; }
        public String getActualDeparture() { return actualDeparture; }
        public String getPlatform() { return platform; }
        public String getDelay() { return delay; }
        public String getStatus() { return status; }
    }
}