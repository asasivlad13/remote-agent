package com.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

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

    public AgentWebSocketClient(String serverUrl, String pcName, String macAddress, String token) {
        super(URI.create(serverUrl));
        this.pcName = pcName;
        this.macAddress = macAddress;
        this.token = token;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("✓ Connected to WebSocket server");

        // Инициализируем захват экрана
        try {
            screenCapture = new ScreenCapture();
            System.out.println("✓ Screen capture ready");
        } catch (Exception e) {
            System.err.println("✗ Failed to init screen capture: " + e.getMessage());
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

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("type", "frame");
                    msg.put("image", base64Image);
                    String json = mapper.writeValueAsString(msg);
                    send(json);

                    System.out.println("📸 Frame sent: " + base64Image.length() + " chars");
                } catch (Exception e) {
                    System.err.println("✗ Error sending frame: " + e.getMessage());
                }
            }
        }, 0, 200); // 200 мс = 5 кадров в секунду
    }

    @Override
    public void onMessage(String message) {
        System.out.println("← Received: " + message);
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