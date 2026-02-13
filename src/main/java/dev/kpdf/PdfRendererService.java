package dev.kpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
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

    // Pre-calculated inversion table for high-speed pixel processing
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

    /**
     * Renders page with fixes for transparency and optional Dark Mode.
     * @param index Page index
     * @param scale Zoom scale (2.0 recommended for high res)
     * @param isDark If true, inverts colors to Dark Mode
     */
    public BufferedImage renderPage(int index, float scale, boolean isDark) throws Exception {
        // 1. Render Raw PDF
        BufferedImage rawImage = renderer.renderImage(index, scale);

        // 2. Create Flattened Image (Removes Transparency)
        BufferedImage fixedImage = new BufferedImage(
                rawImage.getWidth(),
                rawImage.getHeight(),
                BufferedImage.TYPE_INT_RGB // No Alpha Channel = No Transparency Bugs
        );

        Graphics2D g = fixedImage.createGraphics();

        // 3. Fill Background White (Critical step)
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, fixedImage.getWidth(), fixedImage.getHeight());

        // 4. Draw PDF Content
        g.drawImage(rawImage, 0, 0, null);
        g.dispose();

        // 5. Apply Dark Mode (Pixel Inversion)
        if (isDark) {
            // This is extremely fast (native optimization) and guarantees 100% coverage
            return INVERT_OP.filter(fixedImage, null);
        }

        return fixedImage;
    }
}