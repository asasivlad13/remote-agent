package com.agent.video;

import com.agent.video.service.GStreamerManager;

public class VideoStreamService {

    private final GStreamerManager gstreamerManager;

    public VideoStreamService(GStreamerManager gstreamerManager) {
        this.gstreamerManager = gstreamerManager;
    }

    public synchronized void start() throws Exception {
        if (gstreamerManager == null) {
            throw new IllegalStateException("GStreamer manager is not initialized");
        }

        if (gstreamerManager.isRunning()) {
            System.out.println("Video stream already running");
            return;
        }

        gstreamerManager.start();
        System.out.println("✓ Video stream started");
    }

    public synchronized void stop() {
        try {
            if (gstreamerManager == null) {
                System.out.println("Video stream manager is not initialized");
                return;
            }

            if (!gstreamerManager.isRunning()) {
                System.out.println("Video stream already stopped");
                return;
            }

            gstreamerManager.stop();
            System.out.println("✓ Video stream stopped");

        } catch (Exception e) {
            System.err.println("Video stream stop failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized boolean isRunning() {
        return gstreamerManager != null && gstreamerManager.isRunning();
    }
}