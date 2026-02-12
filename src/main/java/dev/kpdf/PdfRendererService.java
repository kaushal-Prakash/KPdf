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

    public BufferedImage renderPage(int index) throws Exception {
        return renderer.renderImageWithDPI(index, 120);
    }
}
