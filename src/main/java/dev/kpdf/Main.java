package dev.kpdf;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        PdfViewer viewer = new PdfViewer(stage);

        Scene scene = new Scene(viewer.getRoot(), 1200, 900);

        // Apply Dark Theme CSS
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm()
        );

        stage.setTitle("KPdf - Pro");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}