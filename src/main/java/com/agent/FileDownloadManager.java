package com.agent;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileDownloadManager {

    private static class DownloadState {
        volatile boolean paused = false;
        volatile boolean cancelled = false;
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, DownloadState> downloads = new ConcurrentHashMap<>();
    private final FileDecryptService fileDecryptService = new FileDecryptService();

    private final long speedLimitBytesPerSecond;

    public FileDownloadManager() {
        this(0);
    }

    public FileDownloadManager(long speedLimitBytesPerSecond) {
        this.speedLimitBytesPerSecond = speedLimitBytesPerSecond;
    }

    public void downloadFile(String fileId,
                             String fileName,
                             String downloadUrl,
                             String encryptionKey,
                             String iv,
                             FileDownloadProgressListener progressListener) throws Exception {
        String safeFileName = sanitizeFileName(fileName);

        DownloadState state = new DownloadState();
        downloads.put(fileId, state);

        Path targetDir = Path.of(
                System.getProperty("user.home"),
                "Downloads",
                "RemotePC"
        );

        Files.createDirectories(targetDir);

        Path encryptedTempPath = targetDir.resolve(safeFileName + ".enc.tmp");
        Path targetPath = targetDir.resolve(safeFileName);

        System.out.println("📥 Encrypted file download started:");
        System.out.println("  ID: " + fileId);
        System.out.println("  File: " + safeFileName);
        System.out.println("  URL: " + downloadUrl);
        System.out.println("  Encrypted temp: " + encryptedTempPath);
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
            downloads.remove(fileId);
            throw new RuntimeException("Download failed, HTTP status: " + response.statusCode());
        }

        long totalBytes = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1);

        long downloadedBytes = 0;
        int lastPercent = -1;

        long speedWindowStart = System.currentTimeMillis();
        long speedWindowBytes = 0;

        try (InputStream inputStream = response.body();
             OutputStream outputStream = Files.newOutputStream(encryptedTempPath)) {

            byte[] buffer = new byte[64 * 1024];

            while (true) {
                if (state.cancelled) {
                    System.out.println("❌ File download cancelled: " + safeFileName);
                    break;
                }

                while (state.paused && !state.cancelled) {
                    Thread.sleep(300);
                }

                int read = inputStream.read(buffer);

                if (read == -1) {
                    break;
                }

                outputStream.write(buffer, 0, read);

                downloadedBytes += read;
                speedWindowBytes += read;

                if (totalBytes > 0) {
                    int percent = (int) ((downloadedBytes * 100) / totalBytes);

                    if (percent != lastPercent) {
                        lastPercent = percent;

                        System.out.println(
                                "📥 Download progress: " + percent + "% / "
                                        + formatBytes(downloadedBytes) + " of "
                                        + formatBytes(totalBytes)
                        );

                        if (progressListener != null) {
                            progressListener.onProgress(
                                    fileId,
                                    safeFileName,
                                    downloadedBytes,
                                    totalBytes,
                                    percent
                            );
                        }
                    }
                } else {
                    System.out.println("📥 Downloaded encrypted: " + formatBytes(downloadedBytes));
                }

                if (speedLimitBytesPerSecond > 0) {
                    long elapsed = System.currentTimeMillis() - speedWindowStart;

                    if (elapsed >= 1000) {
                        speedWindowStart = System.currentTimeMillis();
                        speedWindowBytes = 0;
                    } else if (speedWindowBytes >= speedLimitBytesPerSecond) {
                        long sleepMs = 1000 - elapsed;

                        if (sleepMs > 0) {
                            Thread.sleep(sleepMs);
                        }

                        speedWindowStart = System.currentTimeMillis();
                        speedWindowBytes = 0;
                    }
                }
            }
        } finally {
            downloads.remove(fileId);
        }

        if (state.cancelled) {
            Files.deleteIfExists(encryptedTempPath);
            Files.deleteIfExists(targetPath);
            return;
        }

        System.out.println("🔓 Decrypting file...");

        try (InputStream encryptedInputStream = Files.newInputStream(encryptedTempPath);
             OutputStream decryptedOutputStream = Files.newOutputStream(targetPath)) {
            fileDecryptService.decrypt(
                    encryptedInputStream,
                    decryptedOutputStream,
                    encryptionKey,
                    iv
            );
        }

        Files.deleteIfExists(encryptedTempPath);

        if (progressListener != null) {
            progressListener.onProgress(
                    fileId,
                    safeFileName,
                    downloadedBytes,
                    totalBytes,
                    100
            );
        }

        System.out.println("✅ File downloaded and decrypted:");
        System.out.println("  Target: " + targetPath);
        System.out.println("  Downloaded encrypted size: " + formatBytes(downloadedBytes));

        DesktopNotification.show(
                "Remote PC",
                "Файл получен: " + safeFileName + "<br>Папка: Downloads\\RemotePC"
        );
    }

    public void pauseDownload(String fileId) {
        DownloadState state = downloads.get(fileId);

        if (state != null) {
            state.paused = true;
            System.out.println("⏸ File download paused: " + fileId);
        }
    }

    public void resumeDownload(String fileId) {
        DownloadState state = downloads.get(fileId);

        if (state != null) {
            state.paused = false;
            System.out.println("▶ File download resumed: " + fileId);
        }
    }

    public void cancelDownload(String fileId) {
        DownloadState state = downloads.get(fileId);

        if (state != null) {
            state.cancelled = true;
            state.paused = false;
            System.out.println("❌ File download cancel requested: " + fileId);
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown_file";
        }

        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";

        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);

        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);

        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }
}