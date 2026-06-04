package com.agent.file.listener;

public interface FileDownloadProgressListener {

    void onProgress(String fileId,
                    String fileName,
                    long downloadedBytes,
                    long totalBytes,
                    int percent);

    void onComplete(String fileId,
                    String fileName);
}