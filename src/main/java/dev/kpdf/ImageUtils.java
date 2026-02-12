package dev.kpdf;

import java.awt.image.BufferedImage;
import java.awt.image.LookupOp;
import java.awt.image.ShortLookupTable;

public class ImageUtils {

    // Pre-calculated table for inversion (0 becomes 255, 1 becomes 254...)
    private static final short[] INVERT_TABLE = new short[256];
    static {
        for (int i = 0; i < 256; i++) {
            INVERT_TABLE[i] = (short) (255 - i);
        }
    }

    public static BufferedImage invert(BufferedImage src) {
        // Create a lookup operation (Hardware accelerated by Java 2D)
        ShortLookupTable table = new ShortLookupTable(0, INVERT_TABLE);
        LookupOp op = new LookupOp(table, null);

        // This single line replaces the entire double-for-loop
        return op.filter(src, null);
    }
}