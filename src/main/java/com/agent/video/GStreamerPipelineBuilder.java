package com.agent.video;

import java.util.ArrayList;
import java.util.List;

public class GStreamerPipelineBuilder {

    public List<String> buildWebRtcPipeline(String gstLaunchPath,
                                            int webrtcPort,
                                            String streamName,
                                            int width,
                                            int height,
                                            int fps) {
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
        command.add("video/x-raw(memory:D3D11Memory),format=NV12,width="
                + width
                + ",height="
                + height
                + ",framerate="
                + fps
                + "/1");

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
        command.add("signalling-server-host=0.0.0.0");
        command.add("signalling-server-port=" + webrtcPort);

        command.add("stun-server=stun://stun.l.google.com:19302");
        command.add("video-caps=video/x-h264");

        return command;
    }
}