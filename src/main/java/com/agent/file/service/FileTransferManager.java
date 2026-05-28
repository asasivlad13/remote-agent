package com.agent.file.service;

import com.agent.ui.service.DesktopNotification;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class FileTransferManager {

    private static class TransferState {
        String fileName;
        long fileSize;
        FileOutputStream outputStream;
        long receivedBytes;
    }

    private final Map<String, TransferState> transfers = new HashMap<>();

    public void startTransfer(String transferId, String fileName, long fileSize) throws Exception {
        String safeFileName = sanitizeFileName(fileName);

        File targetDir = Paths.get(
                System.getProperty("user.home"),
                "Downloads",
                "RemotePC"
        ).toFile();

        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File targetFile = new File(targetDir, safeFileName);

        TransferState state = new TransferState();
        state.fileName = safeFileName;
        state.fileSize = fileSize;
        state.outputStream = new FileOutputStream(targetFile);
        state.receivedBytes = 0;

        transfers.put(transferId, state);

        System.out.println("📁 File transfer started:");
        System.out.println("  ID: " + transferId);
        System.out.println("  File: " + safeFileName);
        System.out.println("  Size: " + fileSize + " bytes");
        System.out.println("  Target: " + targetFile.getAbsolutePath());
    }

    public void receiveChunk(String transferId, String base64Chunk) throws Exception {
        TransferState state = transfers.get(transferId);

        if (state == null) {
            System.err.println("Unknown file transfer ID: " + transferId);
            return;
        }

        byte[] data = Base64.getDecoder().decode(base64Chunk);
        state.outputStream.write(data);
        state.receivedBytes += data.length;

        System.out.println("📦 File chunk received: " + state.receivedBytes + "/" + state.fileSize);
    }

    public void finishTransfer(String transferId) throws Exception {
        TransferState state = transfers.remove(transferId);

        if (state == null) {
            System.err.println("Unknown file transfer finish ID: " + transferId);
            return;
        }

        state.outputStream.flush();
        state.outputStream.close();

        System.out.println("✅ File transfer finished:");
        System.out.println("  File: " + state.fileName);
        System.out.println("  Received: " + state.receivedBytes + " bytes");

        DesktopNotification.show(
                "Remote PC",
                "Файл получен: " + state.fileName + "<br>Папка: Downloads\\RemotePC"
        );
    }

    public void cancelTransfer(String transferId) {
        try {
            TransferState state = transfers.remove(transferId);

            if (state != null && state.outputStream != null) {
                state.outputStream.close();
                System.out.println("❌ File transfer cancelled: " + state.fileName);
            }
        } catch (Exception e) {
            System.err.println("File transfer cancel error: " + e.getMessage());
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown_file";
        }

        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}