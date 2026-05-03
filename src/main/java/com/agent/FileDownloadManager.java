package com.agent;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileDownloadManager {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void downloadFile(String fileName, String downloadUrl) throws Exception {
        String safeFileName = sanitizeFileName(fileName);

        Path targetDir = Path.of(
                System.getProperty("user.home"),
                "Downloads",
                "RemotePC"
        );

        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(safeFileName);

        System.out.println("📥 File download started:");
        System.out.println("  File: " + safeFileName);
        System.out.println("  URL: " + downloadUrl);
        System.out.println("  Target: " + targetPath);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("Download failed, HTTP status: " + response.statusCode());
        }

        Files.copy(response.body(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("✅ File downloaded:");
        System.out.println("  Target: " + targetPath);

        DesktopNotification.show(
                "Remote PC",
                "Файл получен: " + safeFileName + "<br>Папка: Downloads\\RemotePC"
        );
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown_file";
        }

        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}