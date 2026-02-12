package dev.kpdf;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageUtils {

    public static BufferedImage invert(BufferedImage img) {

        BufferedImage out =
                new BufferedImage(img.getWidth(), img.getHeight(), img.getType());

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {

                Color c = new Color(img.getRGB(x, y), true);

                Color inv = new Color(
                        255 - c.getRed(),
                        255 - c.getGreen(),
                        255 - c.getBlue()
                );

                out.setRGB(x, y, inv.getRGB());
            }
        }

        return out;
    }
}
