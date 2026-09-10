package com.contextclip.agent;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Generator to create the canonical packaged contextclip-tray.png resource.
 */
public class IconGenerator {

    public static void main(String[] args) throws Exception {
        generateIcon(new File("src/main/resources/icons/contextclip-tray.png"));
    }

    public static void generateIcon(File outputFile) throws Exception {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 1. Dark navy clipboard board (slate-900)
        g2d.setColor(new Color(15, 23, 42));
        g2d.fillRoundRect(3, 4, 26, 26, 6, 6);

        // 2. Clipboard top clip holder (sky-500)
        g2d.setColor(new Color(14, 165, 233));
        g2d.fillRoundRect(10, 1, 12, 6, 3, 3);
        g2d.setColor(new Color(224, 242, 254)); // sky-100 highlight
        g2d.fillRect(12, 3, 8, 2);

        // 3. Inner paper notepad
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(6, 9, 20, 19, 3, 3);

        // 4. Subtle snippet lines on the paper (slate-300)
        g2d.setColor(new Color(203, 213, 225));
        g2d.fillRect(9, 13, 14, 2);
        g2d.fillRect(9, 17, 10, 2);
        g2d.fillRect(9, 21, 7, 2);

        g2d.dispose();

        outputFile.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", outputFile);
        System.out.println("Generated icon at: " + outputFile.getAbsolutePath());
    }
}
