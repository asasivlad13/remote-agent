package com.agent;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ScreenCapture {

    private final Robot robot;
    private final Rectangle screenRect;
    private int targetWidth;
    private int targetHeight;
    private boolean useScaling = false;

    public ScreenCapture() throws AWTException {
        robot = new Robot();
        screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        this.targetWidth = screenRect.width;
        this.targetHeight = screenRect.height;
        System.out.println("✓ Screen capture initialized. Screen size: " + screenRect.width + "x" + screenRect.height);
    }

    public void setResolution(int width, int height) {
        this.targetWidth = width;
        this.targetHeight = height;
        this.useScaling = (width != screenRect.width || height != screenRect.height);
        System.out.println("Resolution set to: " + targetWidth + "x" + targetHeight);
    }

    public BufferedImage captureFullImage() {
        BufferedImage image = robot.createScreenCapture(screenRect);

        if (useScaling) {
            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
            g.dispose();
            return scaled;
        }

        return image;
    }

    public byte[] captureAsJPEG() throws IOException {
        BufferedImage image = captureFullImage();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    public byte[] captureAsWebP() throws IOException {
        BufferedImage image = captureFullImage();
        return WebPEncoder.encode(image);
    }
    public String captureAsBase64() throws IOException {
        byte[] jpegData = captureAsJPEG();
        return java.util.Base64.getEncoder().encodeToString(jpegData);
    }
}