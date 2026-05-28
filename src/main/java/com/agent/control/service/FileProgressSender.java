package com.agent.control.service;

@FunctionalInterface
public interface FileProgressSender {

    void sendFileProgress(String fileId,
                          String fileName,
                          long downloadedBytes,
                          long totalBytes,
                          int percent);
}