package com.agent;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ScreenCapture {

    private final Robot robot;
    private final Rectangle screenRect;
    private int targetWidth = 1280;
    private int targetHeight = 720;

    public ScreenCapture() throws AWTException {
        robot = new Robot();
        screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        System.out.println("✓ Screen capture initialized. Screen size: " + screenRect.width + "x" + screenRect.height);
    }

    public void setResolution(int width, int height) {
        this.targetWidth = width;
        this.targetHeight = height;
        System.out.println("Resolution set to: " + targetWidth + "x" + targetHeight);
    }

    public String captureAsBase64() throws IOException {
        BufferedImage image = robot.createScreenCapture(screenRect);

        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImage.createGraphics();
        g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, "jpg", baos);
        baos.flush();

        return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}