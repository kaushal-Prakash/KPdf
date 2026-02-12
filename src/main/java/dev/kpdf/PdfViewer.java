package dev.kpdf;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.awt.image.BufferedImage;
import java.io.File;

public class PdfViewer {

    private final BorderPane root = new BorderPane();
    private final VBox pages = new VBox(10);
    {
        pages.setStyle("-fx-alignment: center;");
        pages.setStyle("-fx-background-color: #2b2b2b;");
        pages.setPadding(new Insets(20));
    }
    private final ScrollPane scrollPane = new ScrollPane(pages);

    private PDDocument document;
    private PdfRendererService renderer;

    private double zoom = 1.0;
    private boolean inverted = false;

    public PdfViewer(Stage stage) {

        ToolBar toolBar = new ToolBar();

        Button openBtn = new Button("Open");
        Button invertBtn = new Button("Invert");
        Button fitWidthBtn = new Button("Fit Width");
        Button fullBtn = new Button("Fullscreen");
        toolBar.getItems().add(fullBtn);

        fullBtn.setOnAction(e ->
                stage.setFullScreen(!stage.isFullScreen())
        );


        toolBar.getItems().addAll(openBtn, invertBtn, fitWidthBtn);

        root.setTop(toolBar);
        root.setCenter(scrollPane);
        BorderPane.setMargin(scrollPane, new Insets(10));

        openBtn.setOnAction(e -> openPdf(stage));
        invertBtn.setOnAction(e -> toggleInvert());
        fitWidthBtn.setOnAction(e -> fitWidth());

        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        scrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            for (var node : pages.getChildren()) {
                if (node instanceof ImageView iv) {
                    iv.setFitWidth(newVal.getWidth() - 40);
                }
            }
        });

        scrollPane.setOnScroll(event -> {
            if (event.isControlDown()) {
                if (event.getDeltaY() > 0) zoom *= 1.1;
                else zoom /= 1.1;

                pages.setScaleX(zoom);
                pages.setScaleY(zoom);
                event.consume();
            }
        });
    }

    public Parent getRoot() {
        return root;
    }

    private void openPdf(Stage stage) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF", "*.pdf")
            );

            File file = chooser.showOpenDialog(stage);
            if (file == null) return;

            document = PdfRendererService.load(file);
            renderer = new PdfRendererService(document);

            renderAllPages();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void renderAllPages() throws Exception {
        pages.getChildren().clear();

        double viewportWidth = scrollPane.getViewportBounds().getWidth() - 40;

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            BufferedImage img = renderer.renderPage(i);

            if (inverted) img = ImageUtils.invert(img);

            ImageView view = new ImageView(SwingFXUtils.toFXImage(img, null));

            view.setPreserveRatio(true);
            view.setFitWidth(viewportWidth);

            pages.getChildren().add(view);
        }
    }

    private void toggleInvert() {
        inverted = !inverted;
        try {
            renderAllPages();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fitWidth() {
        zoom = scrollPane.getWidth() / pages.getWidth();
        pages.setScaleX(zoom);
        pages.setScaleY(zoom);

    }
}
