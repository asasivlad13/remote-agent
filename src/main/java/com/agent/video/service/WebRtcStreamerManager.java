package com.agent.video.service;

import java.io.File;
import java.io.IOException;

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

    public void start() throws IOException {
        if (process != null && process.isAlive()) {
            System.out.println("WebRTC-Streamer already running");
            return;
        }

        File exeFile = new File(executablePath);
        if (!exeFile.exists()) {
            throw new IOException("webrtc-streamer.exe not found: " + executablePath);
        }

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

    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }

            if (process.isAlive()) {
                process.destroyForcibly();
            }

            System.out.println("WebRTC-Streamer stopped");
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }
}