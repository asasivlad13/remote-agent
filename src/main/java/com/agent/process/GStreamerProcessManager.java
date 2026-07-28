package com.agent.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class GStreamerProcessManager {

    private static final String GST_PROCESS_NAME = "gst-launch-1.0.exe";

    private Process process;

    public synchronized void start(List<String> command,
                                   String gstLaunchPath,
                                   String streamName) throws IOException {
        File exe = new File(gstLaunchPath);

        if (!exe.exists()) {
            throw new IOException("gst-launch-1.0.exe not found: " + gstLaunchPath);
        }

        if (isRunning()) {
            System.out.println("GStreamer already running");
            return;
        }

        stopOldProcesses();

        System.out.println("Starting GStreamer command:");
        System.out.println(String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        process = processBuilder.start();

        startLogReader(process, streamName);

        System.out.println("✓ GStreamer started");
    }

    public synchronized void stop() {
        try {
            if (process != null && process.isAlive()) {
                process.destroy();

                Thread.sleep(1000);

                if (process.isAlive()) {
                    process.destroyForcibly();
                }

                System.out.println("✓ GStreamer stopped");
            }

            process = null;

            stopOldProcesses();

        } catch (Exception e) {
            System.err.println("GStreamer stop error: " + e.getMessage());
        }
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    private void startLogReader(Process processToRead, String streamName) {
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(processToRead.getInputStream()))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    System.out.println(line);

                    if (line.contains("erroneous pipeline")) {
                        System.err.println("✗ GStreamer pipeline error detected");
                    }

                    if (line.contains("registered as [Producer]")) {
                        System.out.println("✓ WebRTC producer registered: " + streamName);
                    }
                }

            } catch (Exception e) {
                System.err.println("GStreamer log read error: " + e.getMessage());
            }
        }, "gstreamer-log-reader");

        logThread.setDaemon(true);
        logThread.start();
    }

    private void stopOldProcesses() {
        try {
            new ProcessBuilder("taskkill", "/F", "/IM", GST_PROCESS_NAME)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();

            System.out.println("Checked and stopped old " + GST_PROCESS_NAME + " processes");

        } catch (Exception e) {
            System.out.println("Could not stop old " + GST_PROCESS_NAME + " processes: " + e.getMessage());
        }
    }
}