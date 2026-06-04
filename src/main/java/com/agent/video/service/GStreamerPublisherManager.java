package com.agent.video.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GStreamerPublisherManager {

    private final String gstLaunchPath;
    private final String rtspUrl;
    private int width;
    private int height;
    private int fps;
    private int bitrateKbps;

    private Process process;

    public GStreamerPublisherManager(String gstLaunchPath,
                                     String rtspUrl,
                                     int width,
                                     int height,
                                     int fps,
                                     int bitrateKbps) {
        this.gstLaunchPath = gstLaunchPath;
        this.rtspUrl = rtspUrl;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrateKbps = bitrateKbps;
    }

    public synchronized void start() throws IOException {
        File exeFile = new File(gstLaunchPath);

        if (!exeFile.exists()) {
            throw new IOException("gst-launch-1.0.exe not found: " + gstLaunchPath);
        }

        /*
         * Важно:
         * перед каждой новой трансляцией полностью очищаем старый GStreamer.
         * Иначе может остаться старый gst-launch-1.0.exe, который забирает ресурсы,
         * держит старый pipeline и даёт 2-3 FPS.
         */
        stopLocalProcess();
        stopOldGStreamerProcesses();

        List<String> cmd = buildCommand();
        ProcessBuilder pb = new ProcessBuilder(cmd);

        if (exeFile.getParentFile() != null) {
            pb.directory(exeFile.getParentFile());
        }

        pb.redirectErrorStream(true);
        pb.inheritIO();

        process = pb.start();

        System.out.println("✓ GStreamer publisher started");
        System.out.println("  RTSP target: " + rtspUrl);
        System.out.println("  Resolution: " + width + "x" + height);
        System.out.println("  FPS: " + fps);
        System.out.println("  Bitrate: " + bitrateKbps + " kbps");
    }

    private List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(gstLaunchPath);
        cmd.add("-e");

        cmd.add("d3d11screencapturesrc");
        cmd.add("!");
        cmd.add("queue");
        cmd.add("leaky=downstream");
        cmd.add("max-size-buffers=2");
        cmd.add("max-size-bytes=0");
        cmd.add("max-size-time=0");

        cmd.add("!");
        cmd.add("videoconvert");

        cmd.add("!");
        cmd.add("videoscale");

        cmd.add("!");
        cmd.add("video/x-raw,width=" + width + ",height=" + height + ",framerate=" + fps + "/1");

        cmd.add("!");
        cmd.add("x264enc");
        cmd.add("tune=zerolatency");
        cmd.add("speed-preset=ultrafast");
        cmd.add("bitrate=" + bitrateKbps);
        cmd.add("key-int-max=" + fps);
        cmd.add("bframes=0");

        cmd.add("!");
        cmd.add("h264parse");
        cmd.add("config-interval=-1");

        cmd.add("!");
        cmd.add("rtspclientsink");
        cmd.add("location=" + rtspUrl);
        cmd.add("protocols=tcp");

        return cmd;
    }

    public synchronized void restartWithPreset(String resolution) throws IOException {
        String[] parts = resolution.split("x");

        if (parts.length != 2) {
            throw new IOException("Invalid resolution preset: " + resolution);
        }

        this.width = Integer.parseInt(parts[0]);
        this.height = Integer.parseInt(parts[1]);

        stop();
        start();
    }

    public synchronized void stop() {
        stopLocalProcess();
        stopOldGStreamerProcesses();
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

                System.out.println("✓ GStreamer publisher stopped");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("GStreamer publisher stop interrupted");
        } finally {
            process = null;
        }
    }

    private void stopOldGStreamerProcesses() {
        runQuietly("taskkill", "/F", "/IM", "gst-launch-1.0.exe");
        System.out.println("Checked and stopped old gst-launch-1.0.exe processes");
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