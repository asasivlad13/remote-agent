package com.agent.ui.service;

import com.fasterxml.jackson.databind.JsonNode;

public class NotificationService {

    public void handleNotification(JsonNode json) {
        String message = json.has("message")
                ? json.get("message").asText()
                : "Unknown notification";

        System.out.println("🔔 NOTIFICATION: " + message);

        DesktopNotification.show(
                "Remote PC",
                message
        );
    }
}