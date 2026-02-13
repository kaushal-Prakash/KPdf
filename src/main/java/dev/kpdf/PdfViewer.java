package dev.kpdf;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
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
    private final VBox loadingOverlay = new VBox(10, new ProgressIndicator(), new Label("Processing..."));
    private final Label zoomLbl = new Label("100%");

    // New UI Elements
    private final TextField pageInput = new TextField();
    private final Label totalPagesLbl = new Label("/ 0");

    // Logic
    private PDDocument document;
    private PdfRendererService renderer;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2);

    // State
    private final DoubleProperty zoomFactor = new SimpleDoubleProperty(1.0);
    private boolean isDarkFilterActive = false;
    private int totalPages = 0;
    private int currentRotation = 0; // 0, 90, 180, 270

    public PdfViewer(Stage stage) {
        setupUI(stage);
        setupEventHandlers();
    }

    public Parent getRoot() {
        return root;
    }

    private void setupUI(Stage stage) {
        // Toolbar
        ToolBar toolBar = new ToolBar();
        toolBar.setStyle("-fx-background-color: #333; -fx-padding: 10px; -fx-spacing: 15px;");

        Button btnOpen = createStyledButton("Open");
        Button btnInvert = createStyledButton("Dark Mode");
        Button btnRotate = createStyledButton("Rotate ⟳");

        Button btnZoomOut = createStyledButton("-");
        Button btnZoomIn = createStyledButton("+");
        Button btnFitWidth = createStyledButton("Fit Width");

        // Zoom Label Style
        zoomLbl.setTextFill(Color.WHITE);
        zoomLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        zoomLbl.setMinWidth(50);
        zoomLbl.setAlignment(Pos.CENTER);

        // Page Navigation
        HBox navBox = new HBox(5);
        navBox.setAlignment(Pos.CENTER);
        pageInput.setPrefWidth(50);
        pageInput.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-alignment: center;");
        totalPagesLbl.setTextFill(Color.LIGHTGRAY);
        navBox.getChildren().addAll(new Label("Go to:"), pageInput, totalPagesLbl);
        ((Label)navBox.getChildren().get(0)).setTextFill(Color.LIGHTGRAY);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolBar.getItems().addAll(
                btnOpen,
                new Separator(),
                btnInvert,
                btnRotate,
                new Separator(),
                btnZoomOut, zoomLbl, btnZoomIn, btnFitWidth,
                spacer,
                navBox
        );

        // Layout
        pagesContainer.setAlignment(Pos.TOP_CENTER);
        pagesContainer.setPadding(new Insets(30));
        pagesContainer.setFillWidth(true);

        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        // Overlays
        pageCounterLabel.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-padding: 8px 15px; -fx-background-radius: 20px;");
        StackPane.setAlignment(pageCounterLabel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(pageCounterLabel, new Insets(20, 30, 20, 0));

        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.8);");
        loadingOverlay.setVisible(false);
        ((Label)loadingOverlay.getChildren().get(1)).setTextFill(Color.WHITE);

        // Assembly
        mainLayout.setTop(toolBar);
        mainLayout.setCenter(scrollPane);
        mainLayout.setBottom(progressBar);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        root.getChildren().addAll(mainLayout, pageCounterLabel, loadingOverlay);

        updateBackgroundStyle();

        // Actions
        btnOpen.setOnAction(e -> openPdf(stage));
        btnZoomIn.setOnAction(e -> animateZoom(0.1));
        btnZoomOut.setOnAction(e -> animateZoom(-0.1));
        btnFitWidth.setOnAction(e -> fitToWidth());

        btnInvert.setOnAction(e -> {
            isDarkFilterActive = !isDarkFilterActive;
            updateBackgroundStyle();
            if (document != null) renderPagesAsync();
        });

        // ROTATE ACTION
        btnRotate.setOnAction(e -> {
            if (document == null) return;
            currentRotation = (currentRotation + 90) % 360;
            renderer.rotateAllPages(currentRotation);
            renderPagesAsync(); // Re-render to update layout and orientation
        });

        // GO TO PAGE ACTION
        pageInput.setOnAction(e -> goToPage());

        zoomFactor.addListener((obs, oldVal, newVal) -> {
            zoomLbl.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
            updatePageWidths();
        });
    }

    private Button createStyledButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #444; -fx-text-fill: #ddd; -fx-cursor: hand; -fx-font-weight: bold;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #666; -fx-text-fill: white;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #444; -fx-text-fill: #ddd;"));
        return btn;
    }

    private void setupEventHandlers() {
        scrollPane.setOnScroll(event -> {
            if (event.isControlDown()) {
                animateZoom(event.getDeltaY() > 0 ? 0.1 : -0.1);
                event.consume();
            }
        });

        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (totalPages > 0) {
                int page = (int) Math.round(newVal.doubleValue() * (totalPages - 1)) + 1;
                // Update Badge
                pageCounterLabel.setText("Page " + Math.max(1, Math.min(page, totalPages)) + " / " + totalPages);
                // Update Text Input (only if not focused to avoid interrupting typing)
                if (!pageInput.isFocused()) {
                    pageInput.setText(String.valueOf(Math.max(1, Math.min(page, totalPages))));
                }
            }
        });

        scrollPane.viewportBoundsProperty().addListener((obs, old, newBounds) -> {
            if (document != null && Math.abs(old.getWidth() - newBounds.getWidth()) > 10) {
                // Optional: fitToWidth();
            }
        });
    }

    private void goToPage() {
        if (totalPages == 0) return;
        try {
            int targetPage = Integer.parseInt(pageInput.getText());
            if (targetPage < 1) targetPage = 1;
            if (targetPage > totalPages) targetPage = totalPages;

            // Calculate scroll position
            // Since pages vary in height (especially if mixed portrait/landscape),
            // the most accurate way is to find the node's position.
            if (!pagesContainer.getChildren().isEmpty()) {
                double targetY = pagesContainer.getChildren().get(targetPage - 1).getBoundsInParent().getMinY();
                double totalHeight = pagesContainer.getBoundsInLocal().getHeight();
                double viewHeight = scrollPane.getViewportBounds().getHeight();

                // VValue is 0.0 to 1.0
                double vValue = targetY / (totalHeight - viewHeight);
                scrollPane.setVvalue(vValue);
            }

            // Unfocus field
            root.requestFocus();

        } catch (NumberFormatException ex) {
            pageInput.setText("1");
        }
    }

    private void updateBackgroundStyle() {
        String color = isDarkFilterActive ? "#1e1e1e" : "#525659";
        root.setStyle("-fx-background-color: " + color + ";");
        pagesContainer.setStyle("-fx-background-color: transparent; -fx-alignment: top-center;");
    }

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
        currentRotation = 0; // Reset rotation on new file

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
            totalPagesLbl.setText("/ " + totalPages);
            pageInput.setText("1");

            renderPagesAsync();
        });

        loadTask.setOnFailed(e -> {
            loadingOverlay.setVisible(false);
            new Alert(Alert.AlertType.ERROR, "Failed to open PDF").show();
        });

        new Thread(loadTask).start();
    }

    private void renderPagesAsync() {
        // Reuse placeholders or clear
        if (!pagesContainer.getChildren().isEmpty()) {
            pagesContainer.getChildren().clear();
        }

        List<ImageView> imageViews = new ArrayList<>();

        for (int i = 0; i < totalPages; i++) {
            ImageView iv = new ImageView();
            iv.setPreserveRatio(true);
            iv.setFitWidth(800);

            // Calculate Placeholder Size with Rotation awareness
            PDRectangle box = document.getPage(i).getMediaBox();

            // Check effective rotation (Document rotation + Page rotation)
            // But since we updated all pages via rotateAllPages(), checking currentRotation is enough
            boolean isLandscape = (currentRotation == 90 || currentRotation == 270);

            double w = isLandscape ? box.getHeight() : box.getWidth();
            double h = isLandscape ? box.getWidth() : box.getHeight();

            double ratio = h / w;
            iv.setFitHeight(800 * ratio);

            imageViews.add(iv);
        }

        pagesContainer.getChildren().addAll(imageViews);
        loadingOverlay.setVisible(false);
        fitToWidth();
        progressBar.setVisible(true);

        Task<Void> renderTask = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i < totalPages; i++) {
                    if (isCancelled()) break;
                    final int index = i;
                    final ImageView target = imageViews.get(index);
                    try {
                        BufferedImage img = renderer.renderPage(index, 2.0f, isDarkFilterActive);
                        javafx.scene.image.Image fxImg = SwingFXUtils.toFXImage(img, null);

                        Platform.runLater(() -> {
                            target.setImage(fxImg);
                            target.setFitHeight(-1);
                            updatePageWidth(target);

                            target.setEffect(isDarkFilterActive ? null : new DropShadow(10, Color.rgb(0,0,0,0.5)));
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
        zoomFactor.set(Math.max(0.1, Math.min(val, 5.0)));
    }

    private void fitToWidth() {
        if(pagesContainer.getChildren().isEmpty()) return;
        double scrollWidth = scrollPane.getViewportBounds().getWidth();
        if(scrollWidth > 0) {
            zoomFactor.set((scrollWidth - 60) / 800.0);
        }
    }
}