package dev.kpdf;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        PdfViewer viewer = new PdfViewer(stage);

        Scene scene = new Scene(viewer.getRoot(), 1300, 900);

        // CSS is optional now as we moved critical styles to Java code
        // to prevent crashes if file is missing.
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            // Ignore if CSS missing, app will still look good
        }

        stage.setTitle("KPdf - Minimal Pdf  Viewer");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}