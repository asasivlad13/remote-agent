package com.agent.config;

import com.agent.network.service.MacAddressProvider;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class AgentConfig {

    private final String wsUrl;
    private final String authUrl;
    private final String pcName;
    private final String username;
    private final String password;

    private final boolean videoEnabled;
    private final String videoPublicUrl;
    private final String videoStreamName;

    private final String gstLaunchPath;
    private final int gstreamerHttpPort;
    private final int gstreamerWebrtcPort;
    private final int gstreamerWidth;
    private final int gstreamerHeight;
    private final int gstreamerFps;
    private final boolean gstreamerRunWebServer;
    private final int gstreamerWebServerPort;

    private AgentConfig(Properties props) {
        this.wsUrl = props.getProperty("server.ws.url");
        this.authUrl = props.getProperty("server.auth.url");
        this.pcName = props.getProperty("pc.name");
        this.username = props.getProperty("auth.username");
        this.password = props.getProperty("auth.password");

        this.videoEnabled = Boolean.parseBoolean(props.getProperty("video.enabled", "true"));
        this.videoPublicUrl = props.getProperty("video.public.url", "http://127.0.0.1:8000");

        String baseStreamName = props.getProperty("video.stream.name", "desktop");
        String macSafe = MacAddressProvider.getMacAddress().replace(":", "").toLowerCase();
        this.videoStreamName = baseStreamName + "_" + macSafe;

        this.gstLaunchPath = props.getProperty(
                "gst.launch.path",
                "C:/gstreamer/1.0/msvc_x86_64/bin/gst-launch-1.0.exe"
        );

        this.gstreamerHttpPort = Integer.parseInt(props.getProperty("gstreamer.http.port", "8000"));
        this.gstreamerWebrtcPort = Integer.parseInt(props.getProperty("gstreamer.webrtc.port", "8443"));
        this.gstreamerWidth = Integer.parseInt(props.getProperty("gstreamer.width", "1280"));
        this.gstreamerHeight = Integer.parseInt(props.getProperty("gstreamer.height", "720"));
        this.gstreamerFps = Integer.parseInt(props.getProperty("gstreamer.fps", "30"));

        this.gstreamerRunWebServer = Boolean.parseBoolean(
                props.getProperty("gstreamer.run.web.server", "false")
        );
        this.gstreamerWebServerPort = Integer.parseInt(
                props.getProperty("gstreamer.web.server.port", "8000")
        );
    }

    public static AgentConfig load() throws Exception {
        Properties props = new Properties();

        try (InputStream input = AgentConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("config.properties not found");
            }

            props.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }

        return new AgentConfig(props);
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public String getPcName() {
        return pcName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isVideoEnabled() {
        return videoEnabled;
    }

    public String getVideoPublicUrl() {
        return videoPublicUrl;
    }

    public String getVideoStreamName() {
        return videoStreamName;
    }

    public String getGstLaunchPath() {
        return gstLaunchPath;
    }

    public int getGstreamerHttpPort() {
        return gstreamerHttpPort;
    }

    public int getGstreamerWebrtcPort() {
        return gstreamerWebrtcPort;
    }

    public int getGstreamerWidth() {
        return gstreamerWidth;
    }

    public int getGstreamerHeight() {
        return gstreamerHeight;
    }

    public int getGstreamerFps() {
        return gstreamerFps;
    }

    public boolean isGstreamerRunWebServer() {
        return gstreamerRunWebServer;
    }

    public int getGstreamerWebServerPort() {
        return gstreamerWebServerPort;
    }
}