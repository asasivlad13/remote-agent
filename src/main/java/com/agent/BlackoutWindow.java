package com.agent;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class BlackoutWindow {

    private static final List<JFrame> windows = new ArrayList<>();

    public static void showBlackout() {
        SwingUtilities.invokeLater(() -> {
            closeWindowsInternal();

            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();

            for (GraphicsDevice screen : screens) {
                Rectangle bounds = screen.getDefaultConfiguration().getBounds();

                JFrame frame = new JFrame();
                frame.setUndecorated(true);
                frame.setAlwaysOnTop(true);
                frame.setResizable(false);
                frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                frame.setBackground(Color.BLACK);

                JPanel panel = new JPanel();
                panel.setBackground(Color.BLACK);
                panel.setOpaque(true);
                frame.setContentPane(panel);

                BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                        cursorImg,
                        new Point(0, 0),
                        "blank"
                );
                frame.setCursor(blankCursor);

                frame.setBounds(bounds);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocusInWindow();

                windows.add(frame);
            }

            System.out.println("Blackout screen enabled");
        });
    }

    public static void hideBlackout() {
        SwingUtilities.invokeLater(() -> {
            closeWindowsInternal();
            System.out.println("Blackout screen disabled");
        });
    }

    private static void closeWindowsInternal() {
        for (JFrame frame : windows) {
            try {
                frame.setVisible(false);
                frame.dispose();
            } catch (Exception ignored) {
            }
        }
        windows.clear();
    }
}