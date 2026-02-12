package dev.kpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;

public class PdfRendererService {

    private final PDDocument document;
    private final PDFRenderer renderer;

    public PdfRendererService(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
    }

    public static PDDocument load(File file) throws Exception {
        return Loader.loadPDF(file);
    }

    /**
     * Renders page with a scaling factor rather than raw DPI.
     * Scale 1.0 ~= 72 DPI. Scale 2.0 ~= 144 DPI (Retina-ish).
     */
    public BufferedImage renderPage(int index, float scale) throws Exception {
        return renderer.renderImage(index, scale);
    }
}