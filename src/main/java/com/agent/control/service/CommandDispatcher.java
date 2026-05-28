package com.agent.control.service;

import com.agent.gamepad.service.VirtualGamepadService;
import com.fasterxml.jackson.databind.JsonNode;

public class CommandDispatcher {

    private final MouseControlService mouseControlService;
    private final KeyboardControlService keyboardControlService;
    private final PowerControlService powerControlService;
    private final FileCommandService fileCommandService;
    private final VirtualGamepadService virtualGamepadService;

    public CommandDispatcher(MouseControlService mouseControlService,
                             KeyboardControlService keyboardControlService,
                             PowerControlService powerControlService,
                             FileCommandService fileCommandService,
                             VirtualGamepadService virtualGamepadService) {

        this.mouseControlService = mouseControlService;
        this.keyboardControlService = keyboardControlService;
        this.powerControlService = powerControlService;
        this.fileCommandService = fileCommandService;
        this.virtualGamepadService = virtualGamepadService;
    }

    public void dispatch(String action, JsonNode json) throws Exception {

        switch (action) {

            case "MOUSE_MOVE":
                mouseControlService.move(json);
                break;

            case "MOUSE_CLICK":
                mouseControlService.click(json);
                break;

            case "MOUSE_WHEEL":
                mouseControlService.wheel(json);
                break;

            case "KEY_PRESS":
                keyboardControlService.press(json);
                break;

            case "KEY_RELEASE":
                keyboardControlService.release(json);
                break;

            case "KEY_COMBO":
                keyboardControlService.pressCtrlAltDelete();
                break;

            case "GAMEPAD_CONNECT":
                virtualGamepadService.connect();
                break;

            case "GAMEPAD_STATE":
                virtualGamepadService.applyState(json);
                break;

            case "GAMEPAD_DISCONNECT":
                virtualGamepadService.disconnect();
                break;

            case "SOFT_SLEEP":
                powerControlService.activateSoftSleep();
                break;

            case "SOFT_WAKE":
                powerControlService.deactivateSoftSleep();
                break;

            case "FILE_START":
                fileCommandService.handleFileStart(json);
                break;

            case "FILE_CHUNK":
                fileCommandService.handleFileChunk(json);
                break;

            case "FILE_END":
                fileCommandService.handleFileEnd(json);
                break;

            case "FILE_DOWNLOAD":
                fileCommandService.handleFileDownload(json);
                break;

            case "FILE_PAUSE":
                fileCommandService.handleFilePause(json);
                break;

            case "FILE_RESUME":
                fileCommandService.handleFileResume(json);
                break;

            case "FILE_CANCEL":
                fileCommandService.handleFileCancel(json);
                break;

            default:
                System.out.println("  → Unknown action: " + action);
        }
    }
}