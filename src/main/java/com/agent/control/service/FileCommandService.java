package com.agent.control.service;

import com.agent.file.service.FileDownloadManager;
import com.agent.file.service.FileTransferManager;
import com.fasterxml.jackson.databind.JsonNode;

public class FileCommandService {

    private final FileTransferManager fileTransferManager;
    private final FileDownloadManager fileDownloadManager;
    private final FileProgressSender progressSender;

    public FileCommandService(FileTransferManager fileTransferManager,
                              FileDownloadManager fileDownloadManager,
                              FileProgressSender progressSender) {
        this.fileTransferManager = fileTransferManager;
        this.fileDownloadManager = fileDownloadManager;
        this.progressSender = progressSender;
    }

    public void handleFileStart(JsonNode json) throws Exception {
        fileTransferManager.startTransfer(
                json.get("transferId").asText(),
                json.get("fileName").asText(),
                json.get("fileSize").asLong()
        );
    }

    public void handleFileChunk(JsonNode json) throws Exception {
        fileTransferManager.receiveChunk(
                json.get("transferId").asText(),
                json.get("chunk").asText()
        );
    }

    public void handleFileEnd(JsonNode json) throws Exception {
        fileTransferManager.finishTransfer(
                json.get("transferId").asText()
        );
    }

    public void handleFileDownload(JsonNode json) {
        String fileId = json.get("fileId").asText();
        String fileName = json.get("fileName").asText();
        String downloadUrl = json.get("downloadUrl").asText();
        String encryptionKey = json.get("encryptionKey").asText();
        String iv = json.get("iv").asText();

        new Thread(() -> {
            try {
                fileDownloadManager.downloadFile(
                        fileId,
                        fileName,
                        downloadUrl,
                        encryptionKey,
                        iv,
                        progressSender::sendFileProgress
                );
            } catch (Exception e) {
                System.err.println("File download error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    public void handleFilePause(JsonNode json) {
        fileDownloadManager.pauseDownload(json.get("fileId").asText());
    }

    public void handleFileResume(JsonNode json) {
        fileDownloadManager.resumeDownload(json.get("fileId").asText());
    }

    public void handleFileCancel(JsonNode json) {
        if (json.has("fileId")) {
            fileDownloadManager.cancelDownload(json.get("fileId").asText());
        } else if (json.has("transferId")) {
            fileTransferManager.cancelTransfer(json.get("transferId").asText());
        } else {
            System.out.println("  → FILE_CANCEL has no fileId or transferId");
        }
    }
}