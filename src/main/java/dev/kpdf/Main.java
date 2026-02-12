package dev.kpdf;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        PdfViewer viewer = new PdfViewer(stage);
        Scene scene = new Scene(viewer.getRoot(), 900, 900);

        stage.setTitle("KPdf");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}