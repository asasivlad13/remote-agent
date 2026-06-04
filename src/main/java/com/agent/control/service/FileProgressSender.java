package com.agent.control.service;

public interface FileProgressSender {

    void sendFileProgress(String fileId,
                          String fileName,
                          long downloadedBytes,
                          long totalBytes,
                          int percent);

    void sendFileDownloadComplete(String fileId,
                                  String fileName);
}