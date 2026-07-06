package com.agent;

import com.agent.auth.service.AuthClient;
import com.agent.config.AgentConfig;
import com.agent.network.client.AgentWebSocketClient;
import com.agent.network.service.MacAddressProvider;
import com.agent.video.VideoStreamService;
import com.agent.video.service.GStreamerManager;

public class AgentApplication {

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("     Remote PC Agent v2.0");
        System.out.println("=========================================");

        try {
            AgentConfig config = AgentConfig.load();

            printConfig(config);

            String macAddress = MacAddressProvider.getMacAddress();
            System.out.println("✓ MAC Address: " + macAddress);

            String token = new AuthClient(config).getToken();
            System.out.println("✓ Token obtained");

            VideoStreamService videoStreamService = null;

            if (config.isVideoEnabled()) {
                GStreamerManager gstreamerManager = new GStreamerManager(
                        config.getGstLaunchPath(),
                        config.getGstreamerHttpPort(),
                        config.getGstreamerWebrtcPort(),
                        config.getVideoStreamName(),
                        config.getGstreamerWidth(),
                        config.getGstreamerHeight(),
                        config.getGstreamerFps(),
                        config.isGstreamerRunWebServer(),
                        config.getGstreamerWebServerPort()
                );

                videoStreamService = new VideoStreamService(gstreamerManager);
                videoStreamService.start();
            }

            AgentWebSocketClient client = new AgentWebSocketClient(
                    config.getWsUrl(),
                    config.getPcName(),
                    macAddress,
                    token,
                    config.getVideoPublicUrl(),
                    config.getVideoStreamName(),
                    videoStreamService
            );

            client.connect();

            System.out.println("✓ Agent started successfully");
            System.out.println("=========================================");

        } catch (Exception e) {
            System.err.println("✗ Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printConfig(AgentConfig config) {
        System.out.println("✓ Config loaded");
        System.out.println("  Server WS: " + config.getWsUrl());
        System.out.println("  PC Name: " + config.getPcName());
        System.out.println("  Video enabled: " + config.isVideoEnabled());
        System.out.println("  Video public url: " + config.getVideoPublicUrl());
        System.out.println("  Video stream name: " + config.getVideoStreamName());
        System.out.println("  gst-launch path: " + config.getGstLaunchPath());
        System.out.println("  GStreamer HTTP port: " + config.getGstreamerHttpPort());
        System.out.println("  GStreamer WebRTC port: " + config.getGstreamerWebrtcPort());
        System.out.println("  GStreamer resolution: "
                + config.getGstreamerWidth()
                + "x"
                + config.getGstreamerHeight());
        System.out.println("  GStreamer FPS: " + config.getGstreamerFps());
        System.out.println("  GStreamer run web server: " + config.isGstreamerRunWebServer());
        System.out.println("  GStreamer web server port: " + config.getGstreamerWebServerPort());
    }
}