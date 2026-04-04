package com.agent;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ScreenCapture {

    private final Robot robot;
    private final Rectangle screenRect;

    public ScreenCapture() throws AWTException {
        robot = new Robot();
        screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        System.out.println("✓ Screen capture initialized. Screen size: " + screenRect.width + "x" + screenRect.height);
    }

    public String captureAsBase64() throws IOException {
        BufferedImage image = robot.createScreenCapture(screenRect);

        // Уменьшаем до 320x240
        int newWidth = 320;
        int newHeight = 240;
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImage.createGraphics();
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, "jpg", baos);
        baos.flush();

        return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}