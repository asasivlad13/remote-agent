package com.agent.auth.service;

import com.agent.config.AgentConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class AuthClient {

    private final AgentConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthClient(AgentConfig config) {
        this.config = config;
    }

    public String getToken() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("username", config.getUsername());
        body.put("password", config.getPassword());

        String json = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getAuthUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("Auth failed: " + response.body());
        }

        JsonNode jsonResponse = mapper.readTree(response.body());
        return jsonResponse.get("token").asText();
    }
}