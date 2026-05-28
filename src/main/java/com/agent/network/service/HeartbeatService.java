package com.agent.network.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class HeartbeatService {

    private final WebSocketClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    private Timer timer;

    public HeartbeatService(WebSocketClient client) {
        this.client = client;
    }

    public void start() {
        timer = new Timer(true);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (!client.isOpen()) {
                        return;
                    }

                    Map<String, String> msg = new HashMap<>();
                    msg.put("type", "heartbeat");

                    client.send(mapper.writeValueAsString(msg));
                    System.out.println("♥ Heartbeat sent");

                } catch (Exception e) {
                    System.err.println("✗ Error sending heartbeat: " + e.getMessage());
                }
            }
        }, 10000, 10000);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }
}