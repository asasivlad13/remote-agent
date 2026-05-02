package com.agent;

import javax.swing.*;
import java.awt.*;

public class DesktopNotification {

    public static void show(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            JWindow window = new JWindow();

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout(10, 6));
            panel.setBackground(new Color(25, 25, 30));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(70, 130, 255), 2),
                    BorderFactory.createEmptyBorder(14, 18, 14, 18)
            ));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setForeground(Color.WHITE);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

            JLabel messageLabel = new JLabel("<html>" + message + "</html>");
            messageLabel.setForeground(new Color(220, 220, 220));
            messageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            panel.add(titleLabel, BorderLayout.NORTH);
            panel.add(messageLabel, BorderLayout.CENTER);

            window.add(panel);
            window.setSize(360, 110);

            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int x = screen.width - window.getWidth() - 24;
            int y = screen.height - window.getHeight() - 48;

            window.setLocation(x, y);
            window.setAlwaysOnTop(true);
            window.setVisible(true);

            new Timer(5000, e -> {
                window.setVisible(false);
                window.dispose();
            }).start();
        });
    }
}