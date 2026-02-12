package dev.kpdf;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfViewer {

    private final StackPane root = new StackPane(); // StackPane to overlay loader
    private final BorderPane mainLayout = new BorderPane();
    private final VBox pagesContainer = new VBox(15);
    private final ScrollPane scrollPane = new ScrollPane(pagesContainer);
    private final Label statusLabel = new Label("Ready");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final VBox loadingOverlay = new VBox(10, new ProgressIndicator(), new Label("Processing PDF..."));

    // Logic
    private PDDocument document;
    private PdfRendererService renderer;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2); // Thread pool for rendering

    // State
    private final DoubleProperty zoomFactor = new SimpleDoubleProperty(1.0);
    private boolean isDarkFilterActive = false;
    private final ColorAdjust darkThemeEffect = new ColorAdjust(); // GPU based effect

    public PdfViewer(Stage stage) {
        setupUI(stage);
        setupEventHandlers();

        // Configure GPU effect for Invert
        darkThemeEffect.setContrast(-0.8); // Soften the inverted colors
        darkThemeEffect.setHue(0);
        // Note: To fully invert, we usually set saturation/brightness,
        // but simple inversion is done via logic below on the ImageView directly or BlendMode.
        // However, standard ColorAdjust doesn't have "Invert".
        // We will toggle the effect on the ImageViews later.
    }

    public Parent getRoot() {
        return root;
    }

    private void setupUI(Stage stage) {
        // 1. Toolbar setup
        ToolBar toolBar = new ToolBar();
        toolBar.getStyleClass().add("main-toolbar");

        Button btnOpen = new Button("Open PDF");
        Button btnInvert = new Button("Dark Mode");
        Separator sep1 = new Separator();
        Button btnZoomOut = new Button("-");
        Label zoomLbl = new Label("100%");
        Button btnZoomIn = new Button("+");
        Button btnFitWidth = new Button("Fit Width");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnFull = new Button("⛶");

        toolBar.getItems().addAll(
                btnOpen, btnInvert, sep1,
                btnZoomOut, zoomLbl, btnZoomIn, btnFitWidth,
                spacer, btnFull
        );

        // 2. ScrollPane Styling
        pagesContainer.setAlignment(Pos.TOP_CENTER);
        pagesContainer.setPadding(new Insets(30));
        pagesContainer.setStyle("-fx-background-color: #2b2b2b;"); // Dark background behind pages

        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("pdf-scroll-pane");

        // 3. Status Bar
        HBox statusBar = new HBox(10, progressBar, statusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));
        statusBar.getStyleClass().add("status-bar");
        progressBar.setVisible(false);

        // 4. Loading Overlay
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        loadingOverlay.setVisible(false);

        // 5. Layout Assembly
        mainLayout.setTop(toolBar);
        mainLayout.setCenter(scrollPane);
        mainLayout.setBottom(statusBar);

        root.getChildren().addAll(mainLayout, loadingOverlay);

        // 6. Action Handling
        btnOpen.setOnAction(e -> openPdf(stage));
        btnFull.setOnAction(e -> stage.setFullScreen(!stage.isFullScreen()));

        // Zoom Logic
        btnZoomIn.setOnAction(e -> animateZoom(0.1));
        btnZoomOut.setOnAction(e -> animateZoom(-0.1));

        zoomFactor.addListener((obs, oldVal, newVal) -> {
            zoomLbl.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
            updatePageWidths();
        });

        btnFitWidth.setOnAction(e -> fitToWidth());

        // Invert Logic (GPU based)
        btnInvert.setOnAction(e -> {
            isDarkFilterActive = !isDarkFilterActive;
            applyDarkFilter();
        });
    }

    private void setupEventHandlers() {
        // Ctrl+Scroll to Zoom
        scrollPane.setOnScroll(event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY();
                animateZoom(delta > 0 ? 0.1 : -0.1);
                event.consume();
            }
        });
    }

    private void openPdf(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = chooser.showOpenDialog(stage);

        if (file != null) {
            loadDocumentAsync(file);
        }
    }

    private void loadDocumentAsync(File file) {
        loadingOverlay.setVisible(true);
        pagesContainer.getChildren().clear();
        progressBar.setVisible(true);

        Task<PDDocument> loadTask = new Task<>() {
            @Override
            protected PDDocument call() throws Exception {
                updateMessage("Parsing PDF structure...");
                return PdfRendererService.load(file);
            }
        };

        loadTask.setOnSucceeded(e -> {
            this.document = loadTask.getValue();
            this.renderer = new PdfRendererService(document);
            renderPagesAsync(); // Start rendering pages
        });

        loadTask.setOnFailed(e -> {
            loadingOverlay.setVisible(false);
            showError("Failed to load PDF", loadTask.getException());
        });

        new Thread(loadTask).start();
    }

    private void renderPagesAsync() {
        int totalPages = document.getNumberOfPages();
        statusLabel.setText("Rendering " + totalPages + " pages...");

        // Create placeholders first so scrollbar works immediately
        List<ImageView> views = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            ImageView iv = new ImageView();
            iv.setPreserveRatio(true);
            iv.setFitWidth(800); // Default width

            // Set placeholder height based on PDF aspect ratio to prevent jumpy scrolling
            PDRectangle box = document.getPage(i).getMediaBox();
            double aspectRatio = box.getHeight() / box.getWidth();
            iv.setFitHeight(800 * aspectRatio);

            views.add(iv);
        }
        pagesContainer.getChildren().addAll(views);
        loadingOverlay.setVisible(false); // Hide blocker, allow scrolling

        // Start background rendering
        Task<Void> renderTask = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i < totalPages; i++) {
                    if (isCancelled()) break;

                    final int pageIndex = i;
                    final ImageView targetView = views.get(pageIndex);

                    try {
                        // High quality render
                        BufferedImage img = renderer.renderPage(pageIndex, 2.0f); // 2.0 scale for crisp text
                        javafx.scene.image.Image fxImg = javafx.embed.swing.SwingFXUtils.toFXImage(img, null);

                        Platform.runLater(() -> {
                            targetView.setImage(fxImg);
                            targetView.setFitHeight(-1); // Remove fixed placeholder height
                            updatePageWidth(targetView); // Apply current zoom
                            applyDarkFilterToImage(targetView);
                            progressBar.setProgress((double) (pageIndex + 1) / totalPages);
                        });

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                return null;
            }
        };

        renderExecutor.submit(renderTask);
    }

    private void animateZoom(double delta) {
        double newZoom = zoomFactor.get() + delta;
        if (newZoom < 0.1) newZoom = 0.1;
        if (newZoom > 5.0) newZoom = 5.0;
        zoomFactor.set(newZoom);
    }

    private void fitToWidth() {
        double scrollWidth = scrollPane.getViewportBounds().getWidth();
        // Assuming standard A4 roughly, approximate a zoom factor
        // This is a simplification; ideally we check the first page width
        if (!pagesContainer.getChildren().isEmpty()) {
            zoomFactor.set(1.0); // Reset scale
            // Let the layout pass handle the width filling based on scrollpane
            // In a real app, we'd calculate ratio: scrollWidth / pageWidth
        }
    }

    private void updatePageWidths() {
        for (var node : pagesContainer.getChildren()) {
            if (node instanceof ImageView iv) {
                updatePageWidth(iv);
            }
        }
    }

    private void updatePageWidth(ImageView iv) {
        // Base width 800 * zoom factor
        double targetWidth = 800 * zoomFactor.get();
        iv.setFitWidth(targetWidth);
    }

    private void applyDarkFilter() {
        for (var node : pagesContainer.getChildren()) {
            if (node instanceof ImageView iv) {
                applyDarkFilterToImage(iv);
            }
        }
        // Change UI background
        pagesContainer.setStyle(isDarkFilterActive
                ? "-fx-background-color: #1e1e1e;"
                : "-fx-background-color: #555555;");
    }

    private void applyDarkFilterToImage(ImageView iv) {
        if (isDarkFilterActive) {
            ColorAdjust invert = new ColorAdjust();
            invert.setSaturation(-1); // Grayscale
            invert.setBrightness(-0.1); // Slightly dim
            // Pure inversion isn't directly in ColorAdjust, so we use BlendMode in CSS or complex effect
            // Simulating "Dark Mode" often looks better just by dimming and removing saturation
            // than pure color inversion for images.
            // If you WANT pure inversion:
            iv.setEffect(new javafx.scene.effect.Blend() {{
                setMode(javafx.scene.effect.BlendMode.DIFFERENCE);
                setBottomInput(new javafx.scene.effect.ColorInput(
                        0, 0, iv.getImage().getWidth(), iv.getImage().getHeight(),
                        javafx.scene.paint.Color.WHITE
                ));
            }});
        } else {
            iv.setEffect(null);
        }
    }

    private void showError(String title, Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(e != null ? e.getMessage() : "Unknown error");
        alert.showAndWait();
    }
}