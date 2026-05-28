package com.agent.video.service;

import java.io.File;
import java.io.IOException;

public class MediaMtxManager {

    private final String executablePath;
    private Process process;

    public MediaMtxManager(String executablePath) {
        this.executablePath = executablePath;
    }

    public void start() throws IOException {
        if (process != null && process.isAlive()) {
            System.out.println("MediaMTX already running");
            return;
        }

        File exeFile = new File(executablePath);
        if (!exeFile.exists()) {
            throw new IOException("mediamtx.exe not found: " + executablePath);
        }

        ProcessBuilder pb = new ProcessBuilder(executablePath);
        if (exeFile.getParentFile() != null) {
            pb.directory(exeFile.getParentFile());
        }

        pb.redirectErrorStream(true);
        pb.inheritIO();

        process = pb.start();
        System.out.println("✓ MediaMTX started");
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
            System.out.println("MediaMTX stopped");
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }
}