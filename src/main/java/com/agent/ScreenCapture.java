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

        // Увеличиваем размер до 1024x768 (вместо 320x240)
        int newWidth = 1024;
        int newHeight = 768;
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImage.createGraphics();
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        // Увеличиваем качество JPEG (настройка не поддерживается напрямую, но размер будет больше)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, "jpg", baos);
        baos.flush();

        return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}