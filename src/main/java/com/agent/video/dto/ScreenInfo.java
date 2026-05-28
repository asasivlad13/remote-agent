package com.agent.video.dto;

import java.awt.*;
import java.awt.geom.AffineTransform;

public class ScreenInfo {
    private final int logicalWidth;
    private final int logicalHeight;
    private final int physicalWidth;
    private final int physicalHeight;
    private final double scaleX;
    private final double scaleY;

    public ScreenInfo(int logicalWidth, int logicalHeight,
                      int physicalWidth, int physicalHeight,
                      double scaleX, double scaleY) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.physicalWidth = physicalWidth;
        this.physicalHeight = physicalHeight;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public static ScreenInfo detect() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension logical = toolkit.getScreenSize();

        GraphicsDevice gd = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();

        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        AffineTransform tx = gc.getDefaultTransform();

        double scaleX = tx.getScaleX();
        double scaleY = tx.getScaleY();

        int logicalWidth = logical.width;
        int logicalHeight = logical.height;

        int physicalWidth = (int) Math.round(logicalWidth * scaleX);
        int physicalHeight = (int) Math.round(logicalHeight * scaleY);

        return new ScreenInfo(
                logicalWidth,
                logicalHeight,
                physicalWidth,
                physicalHeight,
                scaleX,
                scaleY
        );
    }

    public int getLogicalWidth() {
        return logicalWidth;
    }

    public int getLogicalHeight() {
        return logicalHeight;
    }

    public int getPhysicalWidth() {
        return physicalWidth;
    }

    public int getPhysicalHeight() {
        return physicalHeight;
    }

    public double getScaleX() {
        return scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public int toLogicalX(int physicalX) {
        return (int) Math.round(physicalX / scaleX);
    }

    public int toLogicalY(int physicalY) {
        return (int) Math.round(physicalY / scaleY);
    }

    @Override
    public String toString() {
        return "ScreenInfo{" +
                "logical=" + logicalWidth + "x" + logicalHeight +
                ", physical=" + physicalWidth + "x" + physicalHeight +
                ", scaleX=" + scaleX +
                ", scaleY=" + scaleY +
                '}';
    }
}