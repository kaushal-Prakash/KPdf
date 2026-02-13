package dev.kpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.LookupOp;
import java.awt.image.ShortLookupTable;
import java.io.File;

public class PdfRendererService {

    private final PDDocument document;
    private final PDFRenderer renderer;

    // Fast pixel inversion table
    private static final short[] INVERT_TABLE = new short[256];
    static {
        for (int i = 0; i < 256; i++) {
            INVERT_TABLE[i] = (short) (255 - i);
        }
    }
    private static final LookupOp INVERT_OP = new LookupOp(new ShortLookupTable(0, INVERT_TABLE), null);

    public PdfRendererService(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
    }

    public static PDDocument load(File file) throws Exception {
        return Loader.loadPDF(file);
    }

    public BufferedImage renderPage(int index, float scale, boolean isDark) throws Exception {
        // PDFBox renderer automatically respects the page's current rotation
        BufferedImage rawImage = renderer.renderImage(index, scale);

        BufferedImage fixedImage = new BufferedImage(
                rawImage.getWidth(),
                rawImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = fixedImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, fixedImage.getWidth(), fixedImage.getHeight());
        g.drawImage(rawImage, 0, 0, null);
        g.dispose();

        if (isDark) {
            return INVERT_OP.filter(fixedImage, null);
        }
        return fixedImage;
    }

    // --- NEW: Rotation Logic ---
    public void rotateAllPages(int degrees) {
        // degrees should be 0, 90, 180, 270
        for (PDPage page : document.getPages()) {
            page.setRotation(degrees);
        }
    }

    public int getPageRotation(int index) {
        return document.getPage(index).getRotation();
    }
}