package com.agent.file.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;

import java.util.HashMap;
import java.util.Map;

public class FileProgressService {

    private final WebSocketClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileProgressService(WebSocketClient client) {
        this.client = client;
    }

    public void sendFileProgress(String pcName,
                                 String macAddress,
                                 String fileId,
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

            client.send(mapper.writeValueAsString(msg));

        } catch (Exception e) {
            System.err.println("File progress send error: " + e.getMessage());
        }
    }
}