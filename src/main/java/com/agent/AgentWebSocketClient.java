package com.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AgentWebSocketClient extends WebSocketClient {

    private static final int REMOTE_FILE_LIST_LIMIT = 120;
    private static final int REMOTE_FILE_CHUNK_BYTES = 12 * 1024;

    private final String pcName;
    private final String macAddress;
    private final String token;
    private final String webrtcUrl;
    private final String streamName;
    private ScreenInfo screenInfo;
    private final FileDownloadManager fileDownloadManager = new FileDownloadManager(0);

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

            if ("REMOTE_FILE_LIST".equals(type)) {
                handleRemoteFileList(json);
                return;
            }

            if ("REMOTE_FILE_DOWNLOAD".equals(type)) {
                handleRemoteFileDownload(json);
                return;
            }

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

                            if (json.has("ctrl") && json.get("ctrl").asBoolean()) {
                                robot.keyPress(KeyEvent.VK_CONTROL);
                            }
                            if (json.has("alt") && json.get("alt").asBoolean()) {
                                robot.keyPress(KeyEvent.VK_ALT);
                            }
                            if (json.has("shift") && json.get("shift").asBoolean()) {
                                robot.keyPress(KeyEvent.VK_SHIFT);
                            }

                            robot.keyPress(keyCode);
                            Thread.sleep(20);
                            robot.keyRelease(keyCode);

                            if (json.has("shift") && json.get("shift").asBoolean()) {
                                robot.keyRelease(KeyEvent.VK_SHIFT);
                            }
                            if (json.has("alt") && json.get("alt").asBoolean()) {
                                robot.keyRelease(KeyEvent.VK_ALT);
                            }
                            if (json.has("ctrl") && json.get("ctrl").asBoolean()) {
                                robot.keyRelease(KeyEvent.VK_CONTROL);
                            }
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

                    case "FILE_DOWNLOAD": {
                        String fileId = json.get("fileId").asText();
                        String fileName = json.get("fileName").asText();
                        String downloadUrl = json.get("downloadUrl").asText();
                        String encryptionKey = json.get("encryptionKey").asText();
                        String iv = json.get("iv").asText();

                        new Thread(() -> {
                            try {
                                fileDownloadManager.downloadFile(
                                        fileId,
                                        fileName,
                                        downloadUrl,
                                        encryptionKey,
                                        iv,
                                        this::sendFileProgressToServer
                                );
                            } catch (Exception e) {
                                System.err.println("File download error: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }).start();

                        break;
                    }

                    case "FILE_PAUSE": {
                        String fileId = json.get("fileId").asText();
                        fileDownloadManager.pauseDownload(fileId);
                        break;
                    }

                    case "FILE_RESUME": {
                        String fileId = json.get("fileId").asText();
                        fileDownloadManager.resumeDownload(fileId);
                        break;
                    }

                    case "FILE_CANCEL": {
                        if (json.has("fileId")) {
                            String fileId = json.get("fileId").asText();
                            fileDownloadManager.cancelDownload(fileId);
                        } else if (json.has("transferId")) {
                            String transferId = json.get("transferId").asText();
                            fileTransferManager.cancelTransfer(transferId);
                        } else {
                            System.out.println("  → FILE_CANCEL has no fileId or transferId");
                        }
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


    private void handleRemoteFileList(JsonNode json) {
        String requestId = json.has("requestId") ? json.get("requestId").asText() : "";
        String path = json.has("path") && !json.get("path").isNull() ? json.get("path").asText() : "";

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "REMOTE_FILE_LIST_RESULT");
            response.put("requestId", requestId);
            response.put("path", path == null ? "" : path);

            List<Map<String, Object>> items = new ArrayList<>();

            if (path == null || path.isBlank() || "ROOTS".equalsIgnoreCase(path)) {
                File[] roots = File.listRoots();

                if (roots != null) {
                    for (File root : roots) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("name", root.getPath());
                        item.put("path", root.getPath());
                        item.put("directory", true);
                        item.put("size", 0L);
                        item.put("readable", root.canRead());
                        items.add(item);
                    }
                }

                response.put("path", "ROOTS");
                response.put("parentPath", "");
            } else {
                File folder = new File(path);

                if (!folder.exists()) {
                    sendRemoteFileError(requestId, "Папка не найдена: " + path);
                    return;
                }

                if (!folder.isDirectory()) {
                    sendRemoteFileError(requestId, "Указанный путь не является папкой: " + path);
                    return;
                }

                File[] children = folder.listFiles();

                if (children == null) {
                    sendRemoteFileError(requestId, "Нет доступа к папке: " + path);
                    return;
                }

                List<File> sorted = new ArrayList<>(List.of(children));
                sorted.sort(
                        Comparator.comparing(File::isFile)
                                .thenComparing(file -> file.getName().toLowerCase())
                );

                int count = 0;
                boolean truncated = false;

                for (File child : sorted) {
                    if (count >= REMOTE_FILE_LIST_LIMIT) {
                        truncated = true;
                        break;
                    }

                    Map<String, Object> item = new HashMap<>();
                    item.put("name", safeFileName(child));
                    item.put("path", child.getAbsolutePath());
                    item.put("directory", child.isDirectory());
                    item.put("size", child.isFile() ? child.length() : 0L);
                    item.put("readable", child.canRead());
                    items.add(item);
                    count++;
                }

                File parent = folder.getParentFile();
                response.put("parentPath", parent != null ? parent.getAbsolutePath() : "ROOTS");
                response.put("truncated", truncated);
                response.put("limit", REMOTE_FILE_LIST_LIMIT);
            }

            response.put("items", items);

            String responseJson = mapper.writeValueAsString(response);
            System.out.println("→ Remote file list result: " + items.size() + " items, " + responseJson.length() + " chars");
            send(responseJson);
        } catch (Exception e) {
            sendRemoteFileError(requestId, "Ошибка чтения папки: " + e.getMessage());
        }
    }

    private String safeFileName(File file) {
        try {
            String name = file.getName();

            if (name == null || name.isBlank()) {
                name = file.getPath();
            }

            if (name.length() > 120) {
                return name.substring(0, 117) + "...";
            }

            return name;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void handleRemoteFileDownload(JsonNode json) {
        String requestId = json.has("requestId") ? json.get("requestId").asText() : "";
        String path = json.has("path") ? json.get("path").asText() : "";

        new Thread(() -> {
            try {
                File file = new File(path);

                if (!file.exists()) {
                    sendRemoteFileError(requestId, "Файл не найден: " + path);
                    return;
                }

                if (!file.isFile()) {
                    sendRemoteFileError(requestId, "Выбранный путь не является файлом: " + path);
                    return;
                }

                if (!file.canRead()) {
                    sendRemoteFileError(requestId, "Нет доступа к файлу: " + path);
                    return;
                }

                long fileSize = file.length();
                String contentType;
                try {
                    contentType = Files.probeContentType(Path.of(file.getAbsolutePath()));
                } catch (Exception e) {
                    contentType = "application/octet-stream";
                }

                if (contentType == null || contentType.isBlank()) {
                    contentType = "application/octet-stream";
                }

                Map<String, Object> start = new HashMap<>();
                start.put("type", "REMOTE_FILE_DOWNLOAD_START");
                start.put("requestId", requestId);
                start.put("fileName", file.getName());
                start.put("path", file.getAbsolutePath());
                start.put("size", fileSize);
                start.put("contentType", contentType);
                send(mapper.writeValueAsString(start));

                byte[] buffer = new byte[REMOTE_FILE_CHUNK_BYTES];
                long sentBytes = 0;
                int seq = 0;

                try (FileInputStream inputStream = new FileInputStream(file)) {
                    int read;

                    while ((read = inputStream.read(buffer)) != -1) {
                        byte[] part;

                        if (read == buffer.length) {
                            part = buffer;
                        } else {
                            part = java.util.Arrays.copyOf(buffer, read);
                        }

                        sentBytes += read;

                        Map<String, Object> chunk = new HashMap<>();
                        chunk.put("type", "REMOTE_FILE_DOWNLOAD_CHUNK");
                        chunk.put("requestId", requestId);
                        chunk.put("seq", seq++);
                        chunk.put("chunk", Base64.getEncoder().encodeToString(part));
                        chunk.put("sentBytes", sentBytes);
                        chunk.put("totalBytes", fileSize);
                        chunk.put("percent", fileSize > 0 ? (int) Math.min(100, (sentBytes * 100) / fileSize) : 100);
                        send(mapper.writeValueAsString(chunk));
                    }
                }

                Map<String, Object> complete = new HashMap<>();
                complete.put("type", "REMOTE_FILE_DOWNLOAD_COMPLETE");
                complete.put("requestId", requestId);
                complete.put("fileName", file.getName());
                complete.put("size", fileSize);
                send(mapper.writeValueAsString(complete));

            } catch (Exception e) {
                sendRemoteFileError(requestId, "Ошибка скачивания файла: " + e.getMessage());
            }
        }, "remote-file-download-" + requestId).start();
    }

    private void sendRemoteFileError(String requestId, String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("type", "REMOTE_FILE_ERROR");
            error.put("requestId", requestId);
            error.put("message", message);

            send(mapper.writeValueAsString(error));
        } catch (Exception e) {
            System.err.println("Remote file error send failed: " + e.getMessage());
        }
    }

    private void sendFileProgressToServer(String fileId,
                                          String fileName,
                                          long downloadedBytes,
                                          long totalBytes,
                                          int percent) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "FILE_PROGRESS");
            msg.put("pcName", pcName);
            msg.put("mac", macAddress);
            msg.put("fileId", fileId);
            msg.put("fileName", fileName);
            msg.put("downloadedBytes", downloadedBytes);
            msg.put("totalBytes", totalBytes);
            msg.put("percent", percent);

            String json = mapper.writeValueAsString(msg);
            send(json);

        } catch (Exception e) {
            System.err.println("File progress send error: " + e.getMessage());
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