package com.agent.video.service;

import com.agent.process.GStreamerProcessManager;
import com.agent.video.GStreamerPipelineBuilder;

import java.io.IOException;
import java.util.List;

public class GStreamerManager {

    private final String gstLaunchPath;
    private final int httpPort;
    private final int webrtcPort;
    private final String streamName;
    private final int width;
    private final int height;
    private final int fps;
    private final boolean runWebServer;
    private final int webServerPort;

    private final GStreamerPipelineBuilder pipelineBuilder = new GStreamerPipelineBuilder();
    private final GStreamerProcessManager processManager = new GStreamerProcessManager();

    public GStreamerManager(String gstLaunchPath,
                            int httpPort,
                            int webrtcPort,
                            String streamName,
                            int width,
                            int height,
                            int fps,
                            boolean runWebServer,
                            int webServerPort) {
        this.gstLaunchPath = gstLaunchPath;
        this.httpPort = httpPort;
        this.webrtcPort = webrtcPort;
        this.streamName = streamName;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.runWebServer = runWebServer;
        this.webServerPort = webServerPort;
    }

    public void start() throws IOException {
        List<String> command = pipelineBuilder.buildWebRtcPipeline(
                gstLaunchPath,
                webrtcPort,
                streamName,
                width,
                height,
                fps
        );

        processManager.start(
                command,
                gstLaunchPath,
                streamName
        );
    }

    public void stop() {
        processManager.stop();
    }

    public boolean isRunning() {
        return processManager.isRunning();
    }
}