package com.agent;

public interface FileDownloadProgressListener {

    void onProgress(String fileId,
                    String fileName,
                    long downloadedBytes,
                    long totalBytes,
                    int percent);
}