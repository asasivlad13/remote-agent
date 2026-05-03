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

    private final String pcName;
    private final String macAddress;
    private final String token;
    private final String webrtcUrl;
    private final String streamName;
    private ScreenInfo screenInfo;
    private final FileDownloadManager fileDownloadManager = new FileDownloadManager();

    private final ObjectMapper mapper = new ObjectMapper();
    private Timer heartbeatTimer;
    private Robot robot;
    private final FileTransferManager fileTransferManager = new FileTransferManager();


    public AgentWebSocketClient(String serverUrl,
                                String pcName,
                                String macAddress,
                                String token,
                                String webrtcUrl,
                                String streamName) {
        super(URI.create(serverUrl));
        this.pcName = pcName;
        this.macAddress = macAddress;
        this.token = token;
        this.webrtcUrl = webrtcUrl;
        this.streamName = streamName;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("✓ Connected to WebSocket server");

        try {
            robot = new Robot();
            screenInfo = ScreenInfo.detect();

            System.out.println("✓ Robot initialized");
            System.out.println("✓ Screen info detected: " + screenInfo);
        } catch (Exception e) {
            System.err.println("✗ Failed to init Robot/ScreenInfo: " + e.getMessage());
            e.printStackTrace();
        }

        sendRegistration();
        startHeartbeat();
    }

    private void sendRegistration() {
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
                java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
                msg.put("screenWidth", screenSize.width);
                msg.put("screenHeight", screenSize.height);
                msg.put("scaleX", 1.0);
                msg.put("scaleY", 1.0);
            }

            msg.put("webrtcUrl", webrtcUrl);
            msg.put("streamName", streamName);

            String json = mapper.writeValueAsString(msg);
            send(json);

            System.out.println("✓ Registration sent: " + pcName + " (" + macAddress + ")");
            System.out.println("  Physical screen: " + msg.get("screenWidth") + "x" + msg.get("screenHeight"));
            System.out.println("  WebRTC URL: " + webrtcUrl);
            System.out.println("  Stream name: " + streamName);

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

    private void runCommand(String command) throws Exception {
        new ProcessBuilder("cmd.exe", "/c", command)
                .redirectErrorStream(true)
                .start();
    }

    private void runPowerShell(String script) throws Exception {
        new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script
        ).redirectErrorStream(true).start();
    }

    private void setBrightness(int value) {
        try {
            String script =
                    "$brightness = Get-WmiObject -Namespace root/WMI -Class WmiMonitorBrightnessMethods; " +
                            "if ($brightness -ne $null) { $brightness.WmiSetBrightness(1, " + value + "); }";

            runPowerShell(script);
        } catch (Exception e) {
            System.err.println("Brightness change failed: " + e.getMessage());
        }
    }

    private void enableUsbSelectiveSuspend() {
        try {
            runCommand("powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_USB USBSELECTIVE SUSPEND 1");
            runCommand("powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_USB USBSELECTIVE SUSPEND 1");
            runCommand("powercfg /SETACTIVE SCHEME_CURRENT");
            System.out.println("✓ USB selective suspend enabled");
        } catch (Exception e) {
            System.err.println("USB selective suspend enable failed: " + e.getMessage());
        }
    }

    private void disableUsbSelectiveSuspend() {
        try {
            runCommand("powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_USB USBSELECTIVE SUSPEND 0");
            runCommand("powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_USB USBSELECTIVE SUSPEND 0");
            runCommand("powercfg /SETACTIVE SCHEME_CURRENT");
            System.out.println("✓ USB selective suspend disabled");
        } catch (Exception e) {
            System.err.println("USB selective suspend disable failed: " + e.getMessage());
        }
    }

    private void limitCpuForSleep() {
        try {
            runCommand("powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 30");
            runCommand("powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 30");
            runCommand("powercfg /SETACTIVE SCHEME_CURRENT");
            System.out.println("✓ CPU limited to 30%");
        } catch (Exception e) {
            System.err.println("CPU limit failed: " + e.getMessage());
        }
    }

    private void restoreCpuAfterWake() {
        try {
            runCommand("powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 100");
            runCommand("powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 100");
            runCommand("powercfg /SETACTIVE SCHEME_CURRENT");
            System.out.println("✓ CPU restored to 100%");
        } catch (Exception e) {
            System.err.println("CPU restore failed: " + e.getMessage());
        }
    }

    private void muteSound() {
        try {
            runCommand("C:\\Windows\\nircmd.exe mutesysvolume 1");
            System.out.println("✓ Sound muted");
        } catch (Exception e) {
            System.err.println("Mute sound failed: " + e.getMessage());
        }
    }

    private void unmuteSound() {
        try {
            runCommand("C:\\Windows\\nircmd.exe mutesysvolume 0");
            System.out.println("✓ Sound unmuted");
        } catch (Exception e) {
            System.err.println("Unmute sound failed: " + e.getMessage());
        }
    }

    private void stopVideoStream() {
        try {
            GStreamerManager gstreamerManager = AgentApplication.getGStreamerManager();

            if (gstreamerManager != null && gstreamerManager.isRunning()) {
                gstreamerManager.stop();
                System.out.println("✓ Video stream stopped");
            }
        } catch (Exception e) {
            System.err.println("Video stream stop failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startVideoStream() {
        try {
            GStreamerManager gstreamerManager = AgentApplication.getGStreamerManager();

            if (gstreamerManager != null && !gstreamerManager.isRunning()) {
                gstreamerManager.start();
                System.out.println("✓ Video stream started");
            }
        } catch (Exception e) {
            System.err.println("Video stream start failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void activateSoftSleep() {
        try {
            System.out.println("Получена команда умного сна");

            stopVideoStream();

            Thread.sleep(1000);

            runCommand("powercfg /setactive SCHEME_MIN");

            limitCpuForSleep();
            enableUsbSelectiveSuspend();
            muteSound();

            setBrightness(0);

            Thread.sleep(500);

            BlackoutWindow.showBlackout();

            System.out.println("✓ Smart sleep: blackout enabled, brightness 0, video stopped, sound muted, CPU limited, power saver enabled");

        } catch (Exception e) {
            System.err.println("Ошибка умного сна: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deactivateSoftSleep() {
        try {
            System.out.println("Получена команда умного пробуждения");

            BlackoutWindow.hideBlackout();

            Thread.sleep(500);

            setBrightness(70);

            runCommand("powercfg /setactive SCHEME_BALANCED");

            restoreCpuAfterWake();
            disableUsbSelectiveSuspend();
            unmuteSound();

            Thread.sleep(500);

            startVideoStream();

            System.out.println("✓ Smart wake: blackout disabled, brightness restored, sound enabled, CPU restored, video restarted");

        } catch (Exception e) {
            System.err.println("Ошибка умного пробуждения: " + e.getMessage());
            e.printStackTrace();
        }
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
                    case "MOUSE_MOVE": {
                        int physicalX = json.get("x").asInt();
                        int physicalY = json.get("y").asInt();

                        int logicalX = screenInfo.toLogicalX(physicalX);
                        int logicalY = screenInfo.toLogicalY(physicalY);

                        logicalX = Math.max(0, Math.min(logicalX, screenInfo.getLogicalWidth() - 1));
                        logicalY = Math.max(0, Math.min(logicalY, screenInfo.getLogicalHeight() - 1));

                        robot.mouseMove(logicalX, logicalY);
                        break;
                    }

                    case "MOUSE_CLICK": {
                        int physicalX = json.has("x") ? json.get("x").asInt() : -1;
                        int physicalY = json.has("y") ? json.get("y").asInt() : -1;

                        int button = json.has("button") ? json.get("button").asInt() : 1;

                        if (physicalX >= 0 && physicalY >= 0) {
                            int logicalX = screenInfo.toLogicalX(physicalX);
                            int logicalY = screenInfo.toLogicalY(physicalY);

                            logicalX = Math.max(0, Math.min(logicalX, screenInfo.getLogicalWidth() - 1));
                            logicalY = Math.max(0, Math.min(logicalY, screenInfo.getLogicalHeight() - 1));

                            robot.mouseMove(logicalX, logicalY);
                        }

                        int javaButton;
                        switch (button) {
                            case 3:
                                javaButton = InputEvent.BUTTON3_DOWN_MASK;
                                break;
                            case 2:
                                javaButton = InputEvent.BUTTON2_DOWN_MASK;
                                break;
                            default:
                                javaButton = InputEvent.BUTTON1_DOWN_MASK;
                        }

                        robot.mousePress(javaButton);
                        Thread.sleep(50);
                        robot.mouseRelease(javaButton);

                        break;
                    }

                    case "MOUSE_WHEEL": {
                        if (json.has("delta")) {
                            int delta = json.get("delta").asInt();
                            robot.mouseWheel(delta);
                        }
                        break;
                    }

                    case "KEY_PRESS": {
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
                        }
                        break;
                    }

                    case "KEY_RELEASE": {
                        if (json.has("keyCode")) {
                            int releaseCode = json.get("keyCode").asInt();
                            robot.keyRelease(releaseCode);
                        }
                        break;
                    }

                    case "KEY_COMBO": {
                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_ALT);
                        robot.keyPress(KeyEvent.VK_DELETE);
                        Thread.sleep(50);
                        robot.keyRelease(KeyEvent.VK_DELETE);
                        robot.keyRelease(KeyEvent.VK_ALT);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        break;
                    }

                    case "SOFT_SLEEP": {
                        activateSoftSleep();
                        break;
                    }

                    case "SOFT_WAKE": {
                        deactivateSoftSleep();
                        break;
                    }

                    case "FILE_START": {
                        String transferId = json.get("transferId").asText();
                        String fileName = json.get("fileName").asText();
                        long fileSize = json.get("fileSize").asLong();

                        fileTransferManager.startTransfer(transferId, fileName, fileSize);
                        break;
                    }

                    case "FILE_CHUNK": {
                        String transferId = json.get("transferId").asText();
                        String chunk = json.get("chunk").asText();

                        fileTransferManager.receiveChunk(transferId, chunk);
                        break;
                    }

                    case "FILE_END": {
                        String transferId = json.get("transferId").asText();

                        fileTransferManager.finishTransfer(transferId);
                        break;
                    }

                    case "FILE_CANCEL": {
                        String transferId = json.get("transferId").asText();

                        fileTransferManager.cancelTransfer(transferId);
                        break;
                    }

                    case "FILE_DOWNLOAD": {
                        String fileName = json.get("fileName").asText();
                        String downloadUrl = json.get("downloadUrl").asText();

                        new Thread(() -> {
                            try {
                                fileDownloadManager.downloadFile(fileName, downloadUrl);
                            } catch (Exception e) {
                                System.err.println("File download error: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }).start();

                        break;
                    }

                    default:
                        System.out.println("  → Unknown action: " + action);
                }

            } else if ("settings".equals(type)) {
                String resolution = json.has("resolution") ? json.get("resolution").asText() : "unknown";
                System.out.println("Settings requested by client: " + resolution);

            } else if ("notification".equals(type)) {
                String msg = json.has("message") ? json.get("message").asText() : "Unknown notification";
                System.out.println("🔔 NOTIFICATION: " + msg);

                DesktopNotification.show(
                        "Remote PC",
                        msg
                );
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
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("✗ WebSocket error: " + ex.getMessage());
        ex.printStackTrace();
    }
}