package com.smartbank;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FaviconGeneratorTest {

    @Test
    void generateFaviconAssets() throws Exception {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Navy gradient background
        GradientPaint gp = new GradientPaint(0, 0, new Color(0x1e, 0x3a, 0x5f), size, size, new Color(0x0f, 0x27, 0x44));
        g.setPaint(gp);
        g.fill(new RoundRectangle2D.Float(0, 0, size, size, 8, 8));

        // Subtle border
        g.setColor(new Color(0x25, 0x63, 0xeb, 128));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, size - 1, size - 1, 7, 7));

        // Classical Bank Building Icon
        // Pediment (Roof)
        Path2D roof = new Path2D.Float();
        roof.moveTo(16, 5);
        roof.lineTo(5, 11);
        roof.lineTo(27, 11);
        roof.closePath();
        g.setColor(Color.WHITE);
        g.fill(roof);

        // Gold Tympanum
        Path2D goldInner = new Path2D.Float();
        goldInner.moveTo(16, 7);
        goldInner.lineTo(8, 11);
        goldInner.lineTo(24, 11);
        goldInner.closePath();
        g.setColor(new Color(0xf5, 0x9e, 0x0b));
        g.fill(goldInner);

        // Architrave beam
        g.setColor(Color.WHITE);
        g.fillRect(5, 12, 22, 2);

        // 4 Columns
        int[] cols = {7, 12, 17, 22};
        for (int c : cols) {
            g.fillRect(c, 14, 3, 9);
        }

        // Sub-base
        g.fillRect(6, 23, 20, 2);
        // Main base
        g.fillRect(4, 25, 24, 2);
        // Gold base accent
        g.setColor(new Color(0xf5, 0x9e, 0x0b));
        g.fillRect(5, 26, 22, 1);

        g.dispose();

        // Write PNG
        File pngFile = new File("src/main/resources/static/images/favicon.png");
        pngFile.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", pngFile);
        assertTrue(pngFile.exists() && pngFile.length() > 0);

        // Write ICO
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        byte[] pngData = baos.toByteArray();

        ByteArrayOutputStream icoBaos = new ByteArrayOutputStream();
        icoBaos.write(new byte[]{0, 0, 1, 0, 1, 0}); // ICO header
        icoBaos.write(size); // width
        icoBaos.write(size); // height
        icoBaos.write(0); // colors
        icoBaos.write(0); // reserved
        icoBaos.write(new byte[]{1, 0, 32, 0}); // planes 1, bpp 32
        int dataLen = pngData.length;
        icoBaos.write(new byte[]{
                (byte) (dataLen & 0xFF),
                (byte) ((dataLen >> 8) & 0xFF),
                (byte) ((dataLen >> 16) & 0xFF),
                (byte) ((dataLen >> 24) & 0xFF)
        });
        icoBaos.write(new byte[]{22, 0, 0, 0}); // offset 22
        icoBaos.write(pngData);

        File icoFile = new File("src/main/resources/static/favicon.ico");
        try (FileOutputStream fos = new FileOutputStream(icoFile)) {
            icoBaos.writeTo(fos);
        }
        assertTrue(icoFile.exists() && icoFile.length() > 0);
    }
}
