package com.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AgentWebSocketClient extends org.java_websocket.client.WebSocketClient {

    private final String pcName;
    private final String macAddress;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();
    private Timer heartbeatTimer;

    public AgentWebSocketClient(String serverUrl, String pcName, String macAddress, String token) {
        super(URI.create(serverUrl));
        this.pcName = pcName;
        this.macAddress = macAddress;
        this.token = token;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("✓ Connected to WebSocket server");
        sendRegistration();
        startHeartbeat();
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

    @Override
    public void onMessage(String message) {
        System.out.println("← Received: " + message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("✗ Connection closed: " + reason);
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("✗ WebSocket error: " + ex.getMessage());
    }
}