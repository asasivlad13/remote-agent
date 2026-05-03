package com.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class AgentApplication {

    private static String WS_URL;
    private static String AUTH_URL;
    private static String PC_NAME;
    private static String USERNAME;
    private static String PASSWORD;

    private static boolean VIDEO_ENABLED;
    private static GStreamerManager gstreamerManager;
    private static String VIDEO_PUBLIC_URL;
    private static String VIDEO_STREAM_NAME;

    private static String GST_LAUNCH_PATH;
    private static int GSTREAMER_HTTP_PORT;
    private static int GSTREAMER_WEBRTC_PORT;
    private static int GSTREAMER_WIDTH;
    private static int GSTREAMER_HEIGHT;
    private static int GSTREAMER_FPS;

    // Новые параметры для веб-сервера GStreamer
    private static boolean GSTREAMER_RUN_WEB_SERVER;
    private static int GSTREAMER_WEB_SERVER_PORT;

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("     Remote PC Agent v2.0");
        System.out.println("=========================================");

        try {
            loadConfig();

            System.out.println("✓ Config loaded");
            System.out.println("  Server WS: " + WS_URL);
            System.out.println("  PC Name: " + PC_NAME);
            System.out.println("  Video enabled: " + VIDEO_ENABLED);
            System.out.println("  Video public url: " + VIDEO_PUBLIC_URL);
            System.out.println("  Video stream name: " + VIDEO_STREAM_NAME);
            System.out.println("  gst-launch path: " + GST_LAUNCH_PATH);
            System.out.println("  GStreamer HTTP port: " + GSTREAMER_HTTP_PORT);
            System.out.println("  GStreamer WebRTC port: " + GSTREAMER_WEBRTC_PORT);
            System.out.println("  GStreamer resolution: " + GSTREAMER_WIDTH + "x" + GSTREAMER_HEIGHT);
            System.out.println("  GStreamer FPS: " + GSTREAMER_FPS);
            System.out.println("  GStreamer run web server: " + GSTREAMER_RUN_WEB_SERVER);
            System.out.println("  GStreamer web server port: " + GSTREAMER_WEB_SERVER_PORT);

            String macAddress = getMacAddress();
            System.out.println("✓ MAC Address: " + macAddress);

            String token = getToken();
            System.out.println("✓ Token obtained");

            if (VIDEO_ENABLED) {
                gstreamerManager = new GStreamerManager(
                        GST_LAUNCH_PATH,
                        GSTREAMER_HTTP_PORT,
                        GSTREAMER_WEBRTC_PORT,
                        VIDEO_STREAM_NAME,
                        GSTREAMER_WIDTH,
                        GSTREAMER_HEIGHT,
                        GSTREAMER_FPS,
                        GSTREAMER_RUN_WEB_SERVER,
                        GSTREAMER_WEB_SERVER_PORT
                );
                gstreamerManager.start();
            }

            AgentWebSocketClient client = new AgentWebSocketClient(
                    WS_URL,
                    PC_NAME,
                    macAddress,
                    token,
                    VIDEO_PUBLIC_URL,
                    VIDEO_STREAM_NAME
            );

            client.connect();

            System.out.println("✓ Agent started successfully");
            System.out.println("=========================================");

        } catch (Exception e) {
            System.err.println("✗ Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadConfig() throws Exception {
        Properties props = new Properties();

        try (InputStream input = AgentApplication.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("config.properties not found");
            }
            props.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }

        WS_URL = props.getProperty("server.ws.url");
        AUTH_URL = props.getProperty("server.auth.url");
        PC_NAME = props.getProperty("pc.name");
        USERNAME = props.getProperty("auth.username");
        PASSWORD = props.getProperty("auth.password");

        VIDEO_ENABLED = Boolean.parseBoolean(props.getProperty("video.enabled", "true"));
        VIDEO_PUBLIC_URL = props.getProperty("video.public.url", "http://127.0.0.1:8000");

        String baseStreamName = props.getProperty("video.stream.name", "desktop");
        String macSafe = getMacAddress().replace(":", "").toLowerCase();

        VIDEO_STREAM_NAME = baseStreamName + "_" + macSafe;

        GST_LAUNCH_PATH = props.getProperty("gst.launch.path", "C:/gstreamer/1.0/msvc_x86_64/bin/gst-launch-1.0.exe");
        GSTREAMER_HTTP_PORT = Integer.parseInt(props.getProperty("gstreamer.http.port", "8000"));
        GSTREAMER_WEBRTC_PORT = Integer.parseInt(props.getProperty("gstreamer.webrtc.port", "8443"));
        GSTREAMER_WIDTH = Integer.parseInt(props.getProperty("gstreamer.width", "1280"));
        GSTREAMER_HEIGHT = Integer.parseInt(props.getProperty("gstreamer.height", "720"));
        GSTREAMER_FPS = Integer.parseInt(props.getProperty("gstreamer.fps", "30"));

        // Читаем новые параметры для веб-сервера GStreamer
        GSTREAMER_RUN_WEB_SERVER = Boolean.parseBoolean(props.getProperty("gstreamer.run.web.server", "false"));
        GSTREAMER_WEB_SERVER_PORT = Integer.parseInt(props.getProperty("gstreamer.web.server.port", "8000"));
    }

    private static String getToken() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(
                Map.of("username", USERNAME, "password", PASSWORD)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Auth failed: " + response.body());
        }

        JsonNode jsonResponse = mapper.readTree(response.body());
        return jsonResponse.get("token").asText();
    }

    private static String getMacAddress() {
        String macFromConfig = System.getProperty("pc.mac");
        if (macFromConfig != null && !macFromConfig.isEmpty()) {
            System.out.println("  Using MAC from config: " + macFromConfig);
            return macFromConfig;
        }

        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();

                if (mac != null && mac.length == 6) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b)).append(":");
                    }
                    if (sb.length() > 0) sb.setLength(sb.length() - 1);

                    String detectedMac = sb.toString();
                    if (!detectedMac.equals("00:00:00:00:00:00")) {
                        System.out.println("  Auto-detected MAC: " + detectedMac);
                        return detectedMac;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not get MAC: " + e.getMessage());
        }

        System.out.println("  Using fallback MAC: AA:BB:CC:DD:EE:FF");
        return "AA:BB:CC:DD:EE:FF";
    }

    private static class Map {
        static <K, V> java.util.Map<K, V> of(K k1, V v1, K k2, V v2) {
            java.util.Map<K, V> map = new java.util.HashMap<>();
            map.put(k1, v1);
            map.put(k2, v2);
            return map;
        }
    }

    public static GStreamerManager getGStreamerManager() {
        return gstreamerManager;
    }
}