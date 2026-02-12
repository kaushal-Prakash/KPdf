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

        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm()
            );
        } catch (Exception e) {
            System.out.println("CSS not found, using default.");
        }

        stage.setTitle("KPdf - Professional");
        stage.setScene(scene);
        stage.setMaximized(true); // Start fully maximized
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}