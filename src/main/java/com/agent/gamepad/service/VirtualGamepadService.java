package com.agent.gamepad.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class VirtualGamepadService {

    private final ObjectMapper mapper = new ObjectMapper();

    private Process process;
    private BufferedWriter writer;
    private boolean connected;

    public synchronized void connect() {
        ensureStarted();

        if (writer == null) {
            System.out.println("✗ Gamepad helper writer is not ready");
            return;
        }

        sendRaw("{\"type\":\"connect\"}");
        connected = true;
    }

    public synchronized void applyState(JsonNode json) {
        ensureStarted();

        if (writer == null) {
            System.out.println("✗ Gamepad helper writer is not ready, state skipped");
            return;
        }

        if (!connected) {
            sendRaw("{\"type\":\"connect\"}");
            connected = true;
        }

        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("type", "state");

            state.put("lx", getDouble(json, "lx"));
            state.put("ly", getDouble(json, "ly"));
            state.put("rx", getDouble(json, "rx"));
            state.put("ry", getDouble(json, "ry"));

            state.put("lt", getDouble(json, "lt"));
            state.put("rt", getDouble(json, "rt"));

            state.put("a", getBool(json, "a"));
            state.put("b", getBool(json, "b"));
            state.put("x", getBool(json, "x"));
            state.put("y", getBool(json, "y"));

            state.put("lb", getBool(json, "lb"));
            state.put("rb", getBool(json, "rb"));

            state.put("back", getBool(json, "back"));
            state.put("start", getBool(json, "start"));
            state.put("guide", getBool(json, "guide"));

            state.put("ls", getBool(json, "ls"));
            state.put("rs", getBool(json, "rs"));

            state.put("dpadUp", getBool(json, "dpadUp"));
            state.put("dpadDown", getBool(json, "dpadDown"));
            state.put("dpadLeft", getBool(json, "dpadLeft"));
            state.put("dpadRight", getBool(json, "dpadRight"));

            sendRaw(mapper.writeValueAsString(state));

        } catch (Exception e) {
            System.out.println("✗ Failed to apply gamepad state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void disconnect() {
        if (writer != null) {
            sendRaw("{\"type\":\"disconnect\"}");
        }

        connected = false;
    }

    public synchronized void shutdown() {
        try {
            if (writer != null) {
                sendRaw("{\"type\":\"disconnect\"}");
                sendRaw("{\"type\":\"exit\"}");
            }
        } catch (Exception ignored) {
        }

        try {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        } catch (Exception ignored) {
        }

        process = null;
        writer = null;
        connected = false;
    }

    private synchronized void ensureStarted() {
        try {
            if (process != null && process.isAlive() && writer != null) {
                return;
            }

            process = null;
            writer = null;
            connected = false;

            File helperFile = resolveHelperFile();

            System.out.println("Gamepad helper expected path: " + helperFile.getAbsolutePath());

            if (!helperFile.exists()) {
                System.out.println("✗ Gamepad helper not found: " + helperFile.getAbsolutePath());
                return;
            }

            File helperDirectory = helperFile.getParentFile();

            ProcessBuilder processBuilder = new ProcessBuilder(helperFile.getAbsolutePath());
            processBuilder.directory(helperDirectory);
            processBuilder.redirectErrorStream(true);

            process = processBuilder.start();

            writer = new BufferedWriter(
                    new OutputStreamWriter(
                            process.getOutputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            Thread logThread = new Thread(() -> readHelperOutput(process), "gamepad-helper-log");
            logThread.setDaemon(true);
            logThread.start();

            Thread exitThread = new Thread(() -> waitHelperExit(process), "gamepad-helper-exit");
            exitThread.setDaemon(true);
            exitThread.start();

            System.out.println("✓ Gamepad helper process started: " + helperFile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("✗ Failed to start gamepad helper: " + e.getMessage());
            e.printStackTrace();
            process = null;
            writer = null;
            connected = false;
        }
    }

    private File resolveHelperFile() {
        File byWorkingDirectory = new File(
                System.getProperty("user.dir"),
                "gamepad-helper" + File.separator + "GamepadHelper.exe"
        );

        if (byWorkingDirectory.exists()) {
            return byWorkingDirectory;
        }

        return new File(
                "C:" + File.separator +
                        "Users" + File.separator +
                        "асаси" + File.separator +
                        "Desktop" + File.separator +
                        "remote-agentV" + File.separator +
                        "gamepad-helper" + File.separator +
                        "GamepadHelper.exe"
        );
    }

    private void readHelperOutput(Process processToRead) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(processToRead.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println("[gamepad-helper] " + line);
            }

        } catch (Exception e) {
            System.out.println("[gamepad-helper] output read stopped: " + e.getMessage());
        }
    }

    private void waitHelperExit(Process processToWait) {
        try {
            int exitCode = processToWait.waitFor();
            System.out.println("[gamepad-helper] process exited with code " + exitCode);

            synchronized (this) {
                if (process == processToWait) {
                    process = null;
                    writer = null;
                    connected = false;
                }
            }
        } catch (Exception e) {
            System.out.println("[gamepad-helper] wait failed: " + e.getMessage());
        }
    }

    private void sendRaw(String json) {
        try {
            if (writer == null) {
                System.out.println("✗ Gamepad helper writer is null, cannot send: " + json);
                return;
            }

            writer.write(json);
            writer.newLine();
            writer.flush();

        } catch (Exception e) {
            System.out.println("✗ Failed to send to gamepad helper: " + e.getMessage());
            e.printStackTrace();

            try {
                if (process != null) {
                    process.destroy();
                }
            } catch (Exception ignored) {
            }

            process = null;
            writer = null;
            connected = false;
        }
    }

    private double getDouble(JsonNode json, String field) {
        return json.has(field) && json.get(field).isNumber()
                ? json.get(field).asDouble()
                : 0.0;
    }

    private boolean getBool(JsonNode json, String field) {
        return json.has(field) && json.get(field).asBoolean(false);
    }
}
