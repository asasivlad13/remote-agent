package com.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.awt.Robot;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AgentWebSocketClient extends WebSocketClient {

    private final String pcName;
    private final String macAddress;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();
    private Timer heartbeatTimer;
    private Timer screenTimer;
    private ScreenCapture screenCapture;
    private Robot robot;

    public AgentWebSocketClient(String serverUrl, String pcName, String macAddress, String token) {
        super(URI.create(serverUrl));
        this.pcName = pcName;
        this.macAddress = macAddress;
        this.token = token;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("✓ Connected to WebSocket server");

        try {
            screenCapture = new ScreenCapture();
            System.out.println("✓ Screen capture ready");

            robot = new Robot();
            System.out.println("✓ Robot initialized");
        } catch (Exception e) {
            System.err.println("✗ Failed to init: " + e.getMessage());
        }

        sendRegistration();
        startHeartbeat();
        startScreenCapture();
    }

    private void sendRegistration() {
        try {
            Map<String, String> msg = new HashMap<>();
            msg.put("type", "register");
            msg.put("pcName", pcName);
            msg.put("mac", macAddress);
            msg.put("token", token);

            String json = mapper.writeValueAsString(msg);
            send(json);
            System.out.println("✓ Registration sent: " + pcName + " (" + macAddress + ")");
        } catch (Exception e) {
            System.err.println("✗ Error sending registration: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        heartbeatTimer = new Timer(true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Map<String, String> msg = new HashMap<>();
                    msg.put("type", "heartbeat");
                    String json = mapper.writeValueAsString(msg);
                    send(json);
                    System.out.println("♥ Heartbeat sent");
                } catch (Exception e) {
                    System.err.println("✗ Error sending heartbeat: " + e.getMessage());
                }
            }
        }, 10000, 10000);
    }

    private void startScreenCapture() {
        if (screenCapture == null) return;

        screenTimer = new Timer(true);
        screenTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    String base64Image = screenCapture.captureAsBase64();

                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    String json = String.format("{\"mac\":\"%s\",\"image\":\"%s\"}", macAddress, base64Image);

                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://localhost:8080/api/frames/upload"))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                            .thenAccept(response -> {
                                if (response.statusCode() == 200) {
                                    System.out.println("📸 Frame uploaded: " + base64Image.length() + " chars");
                                }
                            });

                } catch (Exception e) {
                    System.err.println("✗ Error sending frame: " + e.getMessage());
                }
            }
        }, 0, 66); // 66 мс ≈ 15 FPS
    }

    @Override
    public void onMessage(String message) {
        System.out.println("← Received command: " + message);

        try {
            JsonNode json = mapper.readTree(message);
            String type = json.get("type").asText();

            if ("command".equals(type)) {
                String action = json.get("action").asText();

                switch (action) {
                    case "MOUSE_MOVE":
                        int x = json.get("x").asInt();
                        int y = json.get("y").asInt();
                        robot.mouseMove(x, y);
                        System.out.println("  → Mouse moved to (" + x + ", " + y + ")");
                        break;

                    case "MOUSE_CLICK":
                        int button = json.get("button").asInt();
                        robot.mousePress(button);
                        Thread.sleep(50);
                        robot.mouseRelease(button);
                        System.out.println("  → Mouse clicked button " + button);
                        break;

                    case "KEY_PRESS":
                        int keyCode = json.get("keyCode").asInt();
                        robot.keyPress(keyCode);
                        robot.keyRelease(keyCode);
                        System.out.println("  → Key pressed: " + keyCode);
                        break;

                    default:
                        System.out.println("  → Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Error executing command: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("✗ Connection closed: " + reason);
        if (heartbeatTimer != null) heartbeatTimer.cancel();
        if (screenTimer != null) screenTimer.cancel();
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("✗ WebSocket error: " + ex.getMessage());
    }
}