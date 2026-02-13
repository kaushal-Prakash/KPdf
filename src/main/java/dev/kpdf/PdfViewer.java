package dev.kpdf;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
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

    private final StackPane root = new StackPane();
    private final BorderPane mainLayout = new BorderPane();
    private final VBox pagesContainer = new VBox(20);
    private final ScrollPane scrollPane = new ScrollPane(pagesContainer);

    // UI Elements
    private final Label pageCounterLabel = new Label("Page 0 / 0");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final VBox loadingOverlay = new VBox(10, new ProgressIndicator(), new Label("Processing PDF..."));
    private final Label zoomLbl = new Label("100%");

    // Logic
    private PDDocument document;
    private PdfRendererService renderer;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2);

    // State
    private final DoubleProperty zoomFactor = new SimpleDoubleProperty(1.0);
    private boolean isDarkFilterActive = false;
    private int totalPages = 0;

    public PdfViewer(Stage stage) {
        setupUI(stage);
        setupEventHandlers();
    }

    public Parent getRoot() {
        return root;
    }

    private void setupUI(Stage stage) {
        // --- 1. Toolbar ---
        ToolBar toolBar = new ToolBar();
        toolBar.getStyleClass().add("main-toolbar");

        Button btnOpen = new Button("Open PDF");
        Button btnInvert = new Button("Dark Mode");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnZoomOut = new Button("-");
        Button btnZoomIn = new Button("+");
        Button btnFitWidth = new Button("Fit Width");

        zoomLbl.getStyleClass().add("zoom-label");

        toolBar.getItems().addAll(
                btnOpen, btnInvert, spacer,
                btnZoomOut, zoomLbl, btnZoomIn, btnFitWidth
        );

        // --- 2. Scroll Pane & Layout ---
        // VBOX SETUP
        pagesContainer.setAlignment(Pos.TOP_CENTER);
        pagesContainer.setPadding(new Insets(30));
        // Important: Ensure VBox fills the ScrollPane width so alignment works
        pagesContainer.setFillWidth(true);

        // SCROLLPANE SETUP
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true); // Ensures background fills height
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("pdf-scroll-pane");

        // --- 3. Badge & Overlay ---
        pageCounterLabel.getStyleClass().add("page-counter");
        StackPane.setAlignment(pageCounterLabel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(pageCounterLabel, new Insets(20, 30, 20, 0));

        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        loadingOverlay.setVisible(false);

        // --- 4. Root Assembly ---
        mainLayout.setTop(toolBar);
        mainLayout.setCenter(scrollPane);
        mainLayout.setBottom(progressBar);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        // Note: The background color is applied to 'root', not ScrollPane
        root.getChildren().addAll(mainLayout, pageCounterLabel, loadingOverlay);

        // Apply Initial Background
        updateBackgroundStyle();

        // --- 5. Actions ---
        btnOpen.setOnAction(e -> openPdf(stage));
        btnZoomIn.setOnAction(e -> animateZoom(0.1));
        btnZoomOut.setOnAction(e -> animateZoom(-0.1));
        btnFitWidth.setOnAction(e -> fitToWidth());

        zoomFactor.addListener((obs, oldVal, newVal) -> {
            zoomLbl.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
            updatePageWidths();
        });

        btnInvert.setOnAction(e -> {
            isDarkFilterActive = !isDarkFilterActive;
            applyDarkFilter();
            updateBackgroundStyle();
        });
    }

    private void setupEventHandlers() {
        scrollPane.setOnScroll(event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY();
                animateZoom(delta > 0 ? 0.1 : -0.1);
                event.consume();
            }
        });

        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (totalPages > 0) {
                int page = (int) Math.round(newVal.doubleValue() * (totalPages - 1)) + 1;
                page = Math.max(1, Math.min(page, totalPages));
                pageCounterLabel.setText("Page " + page + " / " + totalPages);
            }
        });
    }

    // --- CRITICAL FIX: Background Handling ---

    private void updateBackgroundStyle() {
        String color = isDarkFilterActive ? "#1e1e1e" : "#525659";

        // 1. Apply to the ROOT StackPane (Behind everything)
        root.setStyle("-fx-background-color: " + color + ";");

        // 2. Apply to VBox (Container)
        // We MUST re-apply alignment here because setStyle clears it
        pagesContainer.setStyle("-fx-background-color: " + color + "; -fx-alignment: top-center;");
    }

    private void applyDarkFilter() {
        for (var node : pagesContainer.getChildren()) {
            if (node instanceof ImageView iv) applyDarkFilterToImage(iv);
        }
    }

    private void applyDarkFilterToImage(ImageView iv) {
        if (isDarkFilterActive) {
            iv.setEffect(new javafx.scene.effect.Blend() {{
                setMode(javafx.scene.effect.BlendMode.DIFFERENCE);
                setBottomInput(new javafx.scene.effect.ColorInput(
                        0, 0, iv.getImage().getWidth(), iv.getImage().getHeight(),
                        Color.WHITE
                ));
            }});
        } else {
            iv.setEffect(new DropShadow(15, Color.BLACK));
        }
    }

    // --- Core Logic ---

    private void openPdf(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = chooser.showOpenDialog(stage);

        if (file != null) loadDocumentAsync(file);
    }

    private void loadDocumentAsync(File file) {
        loadingOverlay.setVisible(true);
        pagesContainer.getChildren().clear();
        progressBar.setVisible(true);

        Task<PDDocument> loadTask = new Task<>() {
            @Override
            protected PDDocument call() throws Exception {
                return PdfRendererService.load(file);
            }
        };

        loadTask.setOnSucceeded(e -> {
            this.document = loadTask.getValue();
            this.renderer = new PdfRendererService(document);
            this.totalPages = document.getNumberOfPages();
            pageCounterLabel.setText("Page 1 / " + totalPages);
            renderPagesAsync();
        });

        loadTask.setOnFailed(e -> {
            loadingOverlay.setVisible(false);
            new Alert(Alert.AlertType.ERROR, "Failed to open PDF").show();
        });

        new Thread(loadTask).start();
    }

    private void renderPagesAsync() {
        List<ImageView> views = new ArrayList<>();

        for (int i = 0; i < totalPages; i++) {
            ImageView iv = new ImageView();
            iv.setPreserveRatio(true);
            iv.setFitWidth(800);
            PDRectangle box = document.getPage(i).getMediaBox();
            double ratio = box.getHeight() / box.getWidth();
            iv.setFitHeight(800 * ratio);
            views.add(iv);
        }

        pagesContainer.getChildren().addAll(views);
        loadingOverlay.setVisible(false);
        fitToWidth();

        Task<Void> renderTask = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i < totalPages; i++) {
                    if (isCancelled()) break;

                    final int index = i;
                    final ImageView target = views.get(index);

                    try {
                        BufferedImage img = renderer.renderPage(index, 2.0f);
                        javafx.scene.image.Image fxImg = javafx.embed.swing.SwingFXUtils.toFXImage(img, null);

                        Platform.runLater(() -> {
                            target.setImage(fxImg);
                            target.setFitHeight(-1);
                            updatePageWidth(target);
                            applyDarkFilterToImage(target);

                            // Remove individual shadow in dark mode to prevent artifacts
                            if (!isDarkFilterActive) {
                                target.setEffect(new DropShadow(10, Color.rgb(0,0,0,0.5)));
                            }

                            progressBar.setProgress((double)(index + 1) / totalPages);
                            if(index == totalPages -1) progressBar.setVisible(false);
                        });
                    } catch (Exception ex) { ex.printStackTrace(); }
                }
                return null;
            }
        };
        renderExecutor.submit(renderTask);
    }

    private void updatePageWidths() {
        for (var node : pagesContainer.getChildren()) {
            if (node instanceof ImageView iv) updatePageWidth(iv);
        }
    }

    private void updatePageWidth(ImageView iv) {
        iv.setFitWidth(800 * zoomFactor.get());
    }

    private void animateZoom(double delta) {
        double val = zoomFactor.get() + delta;
        if(val < 0.2) val = 0.2;
        if(val > 5.0) val = 5.0;
        zoomFactor.set(val);
    }

    private void fitToWidth() {
        if(pagesContainer.getChildren().isEmpty()) return;
        double viewWidth = scrollPane.getViewportBounds().getWidth();
        if(viewWidth > 0) {
            // Leave 60px margin (30px padding * 2)
            zoomFactor.set((viewWidth - 60) / 800.0);
        }
    }
}