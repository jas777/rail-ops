module com.jas777.railops {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires com.almasb.fxgl.all;
    requires javafx.graphics;
    requires com.fasterxml.jackson.databind;

    opens com.jas777.railops to javafx.fxml;
    exports com.jas777.railops;
}