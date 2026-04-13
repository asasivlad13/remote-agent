package com.agent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public void start() throws IOException {
        if (process != null && process.isAlive()) {
            System.out.println("GStreamer publisher already running");
            return;
        }

        File exeFile = new File(gstLaunchPath);
        if (!exeFile.exists()) {
            throw new IOException("gst-launch-1.0.exe not found: " + gstLaunchPath);
        }

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

        // d3d11screencapturesrc = актуальный Windows screen capture источник
        // videoscale + caps = задаем потоковое разрешение
        // x264enc = кодируем H.264
        // rtspclientsink = публикуем в локальный RTSP сервер (MediaMTX)
        cmd.add("d3d11screencapturesrc");
        cmd.add("!");
        cmd.add("queue");
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
        cmd.add("!");
        cmd.add("h264parse");
        cmd.add("config-interval=-1");
        cmd.add("!");
        cmd.add("rtspclientsink");
        cmd.add("location=" + rtspUrl);
        cmd.add("protocols=tcp");

        return cmd;
    }

    public void restartWithPreset(String resolution) throws IOException {
        String[] parts = resolution.split("x");
        if (parts.length != 2) {
            throw new IOException("Invalid resolution preset: " + resolution);
        }

        this.width = Integer.parseInt(parts[0]);
        this.height = Integer.parseInt(parts[1]);

        stop();
        start();
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
            System.out.println("GStreamer publisher stopped");
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }
}