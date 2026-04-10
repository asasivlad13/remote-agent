package com.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

public class AgentApplication {

    private static String WS_URL;
    private static String AUTH_URL;
    private static String PC_NAME;
    private static String USERNAME;
    private static String PASSWORD;

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("     Remote PC Agent v1.0");
        System.out.println("=========================================");

        try {
            // 1. Загружаем конфигурацию
            loadConfig();
            System.out.println("✓ Config loaded");
            System.out.println("  Server WS: " + WS_URL);
            System.out.println("  PC Name: " + PC_NAME);

            // 2. Получаем MAC-адрес
            String macAddress = getMacAddress();
            System.out.println("✓ MAC Address: " + macAddress);

            // 3. Получаем токен
            String token = getToken();
            System.out.println("✓ Token obtained");

            // 4. Подключаемся к WebSocket
            AgentWebSocketClient client = new AgentWebSocketClient(WS_URL, PC_NAME, macAddress, token);
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
        try (InputStream input = AgentApplication.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("config.properties not found");
            }
            props.load(input);
        }

        WS_URL = props.getProperty("server.ws.url");
        AUTH_URL = props.getProperty("server.auth.url");
        PC_NAME = props.getProperty("pc.name");
        USERNAME = props.getProperty("auth.username");
        PASSWORD = props.getProperty("auth.password");
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
        // Сначала пробуем взять из конфига
        String macFromConfig = System.getProperty("pc.mac");
        if (macFromConfig != null && !macFromConfig.isEmpty()) {
            System.out.println("  Using MAC from config: " + macFromConfig);
            return macFromConfig;
        }

        // Если нет — определяем автоматически
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

    // Вспомогательный класс для Map.of() в Java 9+
    private static class Map {
        static <K, V> java.util.Map<K, V> of(K k1, V v1, K k2, V v2) {
            java.util.Map<K, V> map = new java.util.HashMap<>();
            map.put(k1, v1);
            map.put(k2, v2);
            return map;
        }
    }
}