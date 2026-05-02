package com.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GStreamerManager {

    private final String gstLaunchPath;
    private final int httpPort;
    private final int webrtcPort;
    private final String streamName;
    private final int width;
    private final int height;
    private final int fps;

    private Process process;

    public GStreamerManager(String gstLaunchPath,
                            int httpPort,
                            int webrtcPort,
                            String streamName,
                            int width,
                            int height,
                            int fps) {
        this.gstLaunchPath = gstLaunchPath;
        this.httpPort = httpPort;
        this.webrtcPort = webrtcPort;
        this.streamName = streamName;
        this.width = width;
        this.height = height;
        this.fps = fps;
    }

    public void start() throws IOException {
        File exe = new File(gstLaunchPath);
        if (!exe.exists()) {
            throw new IOException("gst-launch-1.0.exe not found: " + gstLaunchPath);
        }

        if (process != null && process.isAlive()) {
            System.out.println("GStreamer already running");
            return;
        }

        stopOldProcesses();

        List<String> command = new ArrayList<>();
        command.add(gstLaunchPath);
        command.add("-v");

        command.add("d3d11screencapturesrc");
        command.add("capture-api=wgc");
        command.add("show-cursor=false");
        command.add("!");
        command.add("queue");
        command.add("leaky=downstream");
        command.add("max-size-buffers=2");
        command.add("max-size-bytes=0");
        command.add("max-size-time=0");
        command.add("!");
        command.add("d3d11convert");
        command.add("!");
        command.add("video/x-raw(memory:D3D11Memory),format=NV12,width=" + width + ",height=" + height + ",framerate=" + fps + "/1");
        command.add("!");
        command.add("d3d11download");
        command.add("!");
        command.add("videoconvert");
        command.add("!");
        command.add("x264enc");
        command.add("tune=zerolatency");
        command.add("speed-preset=veryfast");
        command.add("bitrate=6000");
        command.add("key-int-max=30");
        command.add("bframes=0");
        command.add("!");
        command.add("video/x-h264,profile=constrained-baseline,stream-format=avc,alignment=au");
        command.add("!");
        command.add("h264parse");
        command.add("config-interval=-1");
        command.add("!");
        command.add("video/x-h264,profile=constrained-baseline,stream-format=avc,alignment=au");
        command.add("!");
        command.add("webrtcsink");
        command.add("name=" + streamName);
        command.add("meta=meta,name=" + streamName);
        command.add("run-signalling-server=true");
        command.add("run-web-server=false");
        command.add("signalling-server-host=0.0.0.0");
        command.add("signalling-server-port=" + webrtcPort);
        command.add("stun-server=stun://stun.l.google.com:19302");
        command.add("video-caps=video/x-h264");

        System.out.println("Starting GStreamer command:");
        System.out.println(String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        process = pb.start();

        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (Exception e) {
                System.err.println("GStreamer log read error: " + e.getMessage());
            }
        });

        logThread.setDaemon(true);
        logThread.start();

        System.out.println("✓ GStreamer started");
    }

    public void stop() {
        try {
            if (process != null && process.isAlive()) {
                process.destroy();

                Thread.sleep(1000);

                if (process.isAlive()) {
                    process.destroyForcibly();
                }

                System.out.println("✓ GStreamer stopped");
            }

            stopOldProcesses();

        } catch (Exception e) {
            System.err.println("GStreamer stop error: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    private void stopOldProcesses() {
        try {
            new ProcessBuilder("taskkill", "/F", "/IM", "gst-launch-1.0.exe")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();

            System.out.println("Checked and stopped old gst-launch-1.0.exe processes");

        } catch (Exception e) {
            System.out.println("Could not stop old gst-launch-1.0.exe processes: " + e.getMessage());
        }
    }
}