package com.agent.network.service;

import com.agent.video.dto.ScreenInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class AgentRegistrationService {

    private final WebSocketClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentRegistrationService(WebSocketClient client) {
        this.client = client;
    }

    public void sendRegistration(String pcName,
                                 String macAddress,
                                 String token,
                                 String webrtcUrl,
                                 String streamName,
                                 ScreenInfo screenInfo) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "register");
            msg.put("pcName", pcName);
            msg.put("mac", macAddress);
            msg.put("token", token);

            if (screenInfo != null) {
                msg.put("screenWidth", screenInfo.getPhysicalWidth());
                msg.put("screenHeight", screenInfo.getPhysicalHeight());
                msg.put("scaleX", screenInfo.getScaleX());
                msg.put("scaleY", screenInfo.getScaleY());
            } else {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                msg.put("screenWidth", screenSize.width);
                msg.put("screenHeight", screenSize.height);
                msg.put("scaleX", 1.0);
                msg.put("scaleY", 1.0);
            }

            msg.put("webrtcUrl", webrtcUrl);
            msg.put("streamName", streamName);

            String json = mapper.writeValueAsString(msg);
            client.send(json);

            System.out.println("✓ Registration sent: " + pcName + " (" + macAddress + ")");
            System.out.println("  Physical screen: " + msg.get("screenWidth") + "x" + msg.get("screenHeight"));
            System.out.println("  WebRTC URL: " + webrtcUrl);
            System.out.println("  Stream name: " + streamName);

        } catch (Exception e) {
            System.err.println("✗ Error sending registration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}