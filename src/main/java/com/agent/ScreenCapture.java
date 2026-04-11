package com.agent;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class ScreenCapture {

    private final Robot robot;
    private final Rectangle screenRect;
    private int targetWidth = 1920;
    private int targetHeight = 1080;

    public ScreenCapture() throws AWTException {
        robot = new Robot();
        screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        System.out.println("✓ Screen capture initialized. Screen size: " + screenRect.width + "x" + screenRect.height);
    }

    public void setResolution(int width, int height) {
        this.targetWidth = width;
        this.targetHeight = height;
        System.out.println("✓ Resolution set to: " + targetWidth + "x" + targetHeight);
    }

    public String captureAsBase64() throws IOException {
        // Захват экрана
        BufferedImage image = robot.createScreenCapture(screenRect);

        // Масштабирование до целевого разрешения
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        // Сжатие в JPEG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, "jpg", baos);
        baos.flush();

        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        System.out.println("  Captured: " + targetWidth + "x" + targetHeight + " -> " + base64.length() + " chars");

        return base64;
    }
}