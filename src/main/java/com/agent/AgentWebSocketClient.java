package com.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AgentWebSocketClient extends WebSocketClient {

    private boolean useWebP = true;

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
            e.printStackTrace();
        }

        sendRegistration();
        startHeartbeat();
        startScreenCapture();
    }

    private void sendRegistration() {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "register");
            msg.put("pcName", pcName);
            msg.put("mac", macAddress);
            msg.put("token", token);

            java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            msg.put("screenWidth", screenSize.width);
            msg.put("screenHeight", screenSize.height);

            String json = mapper.writeValueAsString(msg);
            send(json);
            System.out.println("✓ Registration sent: " + pcName + " (" + macAddress + ") screen: " + screenSize.width + "x" + screenSize.height);
        } catch (Exception e) {
            System.err.println("✗ Error sending registration: " + e.getMessage());
            e.printStackTrace();
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
                    e.printStackTrace();
                }
            }
        }, 10000, 10000);
    }

    private void startScreenCapture() {
        if (screenCapture == null) return;
        startScreenCaptureWithInterval(16); // 16ms = 60 FPS
    }

    private void startScreenCaptureWithInterval(int intervalMs) {
        if (screenTimer != null) screenTimer.cancel();
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
                                    // Оптимизация: не выводим каждый кадр
                                    // System.out.println("📸 Frame uploaded: " + base64Image.length() + " chars");
                                }
                            });

                } catch (Exception e) {
                    System.err.println("✗ Error sending frame: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, intervalMs);
    }

    @Override
    public void onMessage(String message) {
        System.out.println("← Received: " + message);

        try {
            JsonNode json = mapper.readTree(message);

            if (json.has("status")) {
                String status = json.get("status").asText();
                System.out.println("✓ Server status: " + status);
                return;
            }

            if (!json.has("type")) {
                System.out.println("  → Message has no type field, skipping");
                return;
            }

            String type = json.get("type").asText();

            if ("command".equals(type)) {
                if (!json.has("action")) {
                    System.out.println("  → Command has no action field");
                    return;
                }
                String action = json.get("action").asText();

                switch (action) {
                    case "MOUSE_MOVE":
                        if (json.has("x") && json.has("y")) {
                            int x = json.get("x").asInt();
                            int y = json.get("y").asInt();
                            robot.mouseMove(x, y);
                            System.out.println("  → Mouse moved to (" + x + ", " + y + ")");
                        }
                        break;

                    case "MOUSE_CLICK":
                        int button = json.has("button") ? json.get("button").asInt() : 1;
                        int javaButton;
                        switch (button) {
                            case 1: javaButton = InputEvent.BUTTON1_DOWN_MASK; break;
                            case 2: javaButton = InputEvent.BUTTON2_DOWN_MASK; break;
                            case 3: javaButton = InputEvent.BUTTON3_DOWN_MASK; break;
                            default: javaButton = InputEvent.BUTTON1_DOWN_MASK;
                        }
                        robot.mousePress(javaButton);
                        Thread.sleep(50);
                        robot.mouseRelease(javaButton);
                        System.out.println("  → Mouse clicked button " + button);
                        break;

                    case "MOUSE_WHEEL":
                        if (json.has("delta")) {
                            int delta = json.get("delta").asInt();
                            robot.mouseWheel(delta);
                            System.out.println("  → Mouse wheel: " + delta);
                        }
                        break;

                    case "KEY_PRESS":
                        if (json.has("keyCode")) {
                            int keyCode = json.get("keyCode").asInt();

                            if (json.has("ctrl") && json.get("ctrl").asBoolean()) robot.keyPress(KeyEvent.VK_CONTROL);
                            if (json.has("alt") && json.get("alt").asBoolean()) robot.keyPress(KeyEvent.VK_ALT);
                            if (json.has("shift") && json.get("shift").asBoolean()) robot.keyPress(KeyEvent.VK_SHIFT);

                            robot.keyPress(keyCode);
                            Thread.sleep(20);
                            robot.keyRelease(keyCode);

                            if (json.has("shift") && json.get("shift").asBoolean()) robot.keyRelease(KeyEvent.VK_SHIFT);
                            if (json.has("alt") && json.get("alt").asBoolean()) robot.keyRelease(KeyEvent.VK_ALT);
                            if (json.has("ctrl") && json.get("ctrl").asBoolean()) robot.keyRelease(KeyEvent.VK_CONTROL);

                            System.out.println("  → Key pressed: " + keyCode);
                        }
                        break;

                    case "KEY_RELEASE":
                        if (json.has("keyCode")) {
                            int releaseCode = json.get("keyCode").asInt();
                            robot.keyRelease(releaseCode);
                            System.out.println("  → Key released: " + releaseCode);
                        }
                        break;

                    case "KEY_COMBO":
                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_ALT);
                        robot.keyPress(KeyEvent.VK_DELETE);
                        Thread.sleep(50);
                        robot.keyRelease(KeyEvent.VK_DELETE);
                        robot.keyRelease(KeyEvent.VK_ALT);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        System.out.println("  → Combo: Ctrl+Alt+Del");
                        break;

                    default:
                        System.out.println("  → Unknown action: " + action);
                }
            } else if ("settings".equals(type)) {
                if (json.has("resolution") && json.has("fps")) {
                    String resolution = json.get("resolution").asText();
                    int fps = json.get("fps").asInt();

                    int width, height;
                    switch (resolution) {
                        case "360": width = 640; height = 360; break;
                        case "480": width = 854; height = 480; break;
                        case "720": width = 1280; height = 720; break;
                        case "1080": width = 1920; height = 1080; break;
                        case "1440": width = 2560; height = 1440; break;
                        case "2160": width = 3840; height = 2160; break;
                        default: width = 1280; height = 720;
                    }
                    screenCapture.setResolution(width, height);

                    int intervalMs = 1000 / fps;
                    startScreenCaptureWithInterval(intervalMs);

                    System.out.println("Settings applied: " + resolution + "p, " + fps + " FPS");
                }
            } else if ("notification".equals(type)) {
                String msg = json.has("message") ? json.get("message").asText() : "Unknown notification";
                System.out.println("🔔 NOTIFICATION: " + msg);

                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        String psCommand = "powershell.exe -Command \"& {Add-Type -AssemblyName System.Windows.Forms; " +
                                "$notification = New-Object System.Windows.Forms.NotifyIcon; " +
                                "$notification.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon((Get-Process -Id $pid).Path); " +
                                "$notification.BalloonTipTitle = 'Remote PC'; " +
                                "$notification.BalloonTipText = '" + msg + "'; " +
                                "$notification.Visible = $true; " +
                                "$notification.ShowBalloonTip(5000)}\"";
                        Runtime.getRuntime().exec(psCommand);
                    } else if (os.contains("linux") || os.contains("mac")) {
                        Runtime.getRuntime().exec(new String[]{"notify-send", "Remote PC", msg});
                    }
                } catch (Exception e) {
                    System.err.println("Could not show notification: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Error processing message: " + e.getMessage());
            e.printStackTrace();
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
        ex.printStackTrace();
    }
}