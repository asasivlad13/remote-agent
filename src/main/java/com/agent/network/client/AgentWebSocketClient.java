package com.agent.network.client;

import com.agent.file.service.FileDownloadManager;
import com.agent.file.service.FileTransferManager;
import com.agent.gamepad.service.VirtualGamepadService;
import com.agent.network.service.HeartbeatService;
import com.agent.video.dto.ScreenInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.agent.network.service.AgentRegistrationService;
import com.agent.control.service.MouseControlService;
import com.agent.control.service.KeyboardControlService;
import com.agent.control.service.PowerControlService;
import com.agent.file.service.RemoteFileBrowserService;
import com.agent.control.service.FileCommandService;
import com.agent.control.service.CommandDispatcher;
import com.agent.ui.service.NotificationService;
import com.agent.file.service.FileProgressService;

import java.awt.Robot;
import java.net.URI;


public class AgentWebSocketClient extends WebSocketClient {

    private FileProgressService fileProgressService;
    private final NotificationService notificationService = new NotificationService();
    private FileCommandService fileCommandService;
    private final PowerControlService powerControlService = new PowerControlService();
    private MouseControlService mouseControlService;
    private KeyboardControlService keyboardControlService;
    private AgentRegistrationService registrationService;
    private HeartbeatService heartbeatService;
    private final String pcName;
    private final String macAddress;
    private final String token;
    private final String webrtcUrl;
    private final String streamName;
    private ScreenInfo screenInfo;
    private final FileDownloadManager fileDownloadManager = new FileDownloadManager(0);
    private RemoteFileBrowserService remoteFileBrowserService;
    private CommandDispatcher commandDispatcher;

    private final ObjectMapper mapper = new ObjectMapper();
    private Robot robot;
    private final FileTransferManager fileTransferManager = new FileTransferManager();
    private final VirtualGamepadService virtualGamepadService = new VirtualGamepadService();


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

            mouseControlService = new MouseControlService(robot, screenInfo);
            keyboardControlService = new KeyboardControlService(robot);

            System.out.println("✓ Robot initialized");
            System.out.println("✓ Screen info detected: " + screenInfo);
        } catch (Exception e) {
            System.err.println("✗ Failed to init Robot/ScreenInfo: " + e.getMessage());
            e.printStackTrace();
        }

        registrationService = new AgentRegistrationService(this);
        registrationService.sendRegistration(
                pcName,
                macAddress,
                token,
                webrtcUrl,
                streamName,
                screenInfo
        );
        heartbeatService = new HeartbeatService(this);
        heartbeatService.start();

        remoteFileBrowserService = new RemoteFileBrowserService(this);

        fileProgressService = new FileProgressService(this);

        fileCommandService = new FileCommandService(
                fileTransferManager,
                fileDownloadManager,
                new com.agent.control.service.FileProgressSender() {
                    @Override
                    public void sendFileProgress(String fileId,
                                                 String fileName,
                                                 long downloadedBytes,
                                                 long totalBytes,
                                                 int percent) {
                        fileProgressService.sendFileProgress(
                                pcName,
                                macAddress,
                                fileId,
                                fileName,
                                downloadedBytes,
                                totalBytes,
                                percent
                        );
                    }

                    @Override
                    public void sendFileDownloadComplete(String fileId, String fileName) {
                        fileProgressService.sendFileDownloadComplete(
                                pcName,
                                macAddress,
                                fileId,
                                fileName
                        );
                    }
                }
        );

        commandDispatcher = new CommandDispatcher(
                mouseControlService,
                keyboardControlService,
                powerControlService,
                fileCommandService,
                 virtualGamepadService
        );
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
                remoteFileBrowserService.handleRemoteFileList(json);
                return;
            }

            if ("REMOTE_FILE_DOWNLOAD".equals(type)) {
                remoteFileBrowserService.handleRemoteFileDownload(json);
                return;
            }

            if ("command".equals(type)) {
                if (!json.has("action")) {
                    System.out.println("  → Command has no action field");
                    return;
                }

                String action = json.get("action").asText();

                commandDispatcher.dispatch(action, json);

            } else if ("settings".equals(type)) {
                String resolution = json.has("resolution") ? json.get("resolution").asText() : "unknown";
                System.out.println("Settings requested by client: " + resolution);

            } else if ("notification".equals(type)) {
                notificationService.handleNotification(json);
            }
        } catch (Exception e) {
            System.err.println("✗ Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("✗ WebSocket connection closed: " + reason);

        if (heartbeatService != null) {
            heartbeatService.stop();
        }
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("✗ WebSocket error: " + ex.getMessage());
        ex.printStackTrace();
    }

}