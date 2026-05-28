package com.agent.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemoteFileBrowserService {

    private static final int REMOTE_FILE_LIST_LIMIT = 120;
    private static final int REMOTE_FILE_CHUNK_BYTES = 12 * 1024;

    private final WebSocketClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public RemoteFileBrowserService(WebSocketClient client) {
        this.client = client;
    }

    public void handleRemoteFileList(JsonNode json) {
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
            client.send(responseJson);

        } catch (Exception e) {
            sendRemoteFileError(requestId, "Ошибка чтения папки: " + e.getMessage());
        }
    }

    public void handleRemoteFileDownload(JsonNode json) {
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
                client.send(mapper.writeValueAsString(start));

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

                        client.send(mapper.writeValueAsString(chunk));
                    }
                }

                Map<String, Object> complete = new HashMap<>();
                complete.put("type", "REMOTE_FILE_DOWNLOAD_COMPLETE");
                complete.put("requestId", requestId);
                complete.put("fileName", file.getName());
                complete.put("size", fileSize);

                client.send(mapper.writeValueAsString(complete));

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

            client.send(mapper.writeValueAsString(error));
        } catch (Exception e) {
            System.err.println("Remote file error send failed: " + e.getMessage());
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
}