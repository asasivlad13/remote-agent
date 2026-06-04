package com.agent.video.service;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WebRtcStreamerManager {

    private final String executablePath;
    private final int port;
    private final String streamName;
    private final String sourceUrl;

    private Process process;

    public WebRtcStreamerManager(String executablePath, int port, String streamName, String sourceUrl) {
        this.executablePath = executablePath;
        this.port = port;
        this.streamName = streamName;
        this.sourceUrl = sourceUrl;
    }

    public synchronized void start() throws IOException {
        File exeFile = new File(executablePath);

        if (!exeFile.exists()) {
            throw new IOException("webrtc-streamer.exe not found: " + executablePath);
        }

        /*
         * Перед новым запуском чистим старый WebRTC-Streamer.
         * Иначе старый процесс может держать порт или старый RTSP-поток.
         */
        stopLocalProcess();
        stopOldWebRtcStreamerProcesses();

        ProcessBuilder pb = new ProcessBuilder(
                executablePath,
                "-H", "0.0.0.0:" + port,
                "-n", streamName,
                "-u", sourceUrl
        );

        if (exeFile.getParentFile() != null) {
            pb.directory(exeFile.getParentFile());
        }

        pb.redirectErrorStream(true);
        pb.inheritIO();

        process = pb.start();

        System.out.println("✓ WebRTC-Streamer started on port " + port + ", stream=" + streamName);
        System.out.println("  Source URL: " + sourceUrl);
    }

    public synchronized void stop() {
        stopLocalProcess();
        stopOldWebRtcStreamerProcesses();
    }

    private void stopLocalProcess() {
        if (process == null) {
            return;
        }

        try {
            if (process.isAlive()) {
                process.destroy();

                boolean stopped = process.waitFor(1500, TimeUnit.MILLISECONDS);

                if (!stopped && process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(1500, TimeUnit.MILLISECONDS);
                }

                System.out.println("✓ WebRTC-Streamer stopped");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("WebRTC-Streamer stop interrupted");
        } finally {
            process = null;
        }
    }

    private void stopOldWebRtcStreamerProcesses() {
        runQuietly("taskkill", "/F", "/IM", "webrtc-streamer.exe");
        System.out.println("Checked and stopped old webrtc-streamer.exe processes");
    }

    private void runQuietly(String... command) {
        try {
            Process cleanup = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            cleanup.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Если процесса нет — это нормально.
        }
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }
}