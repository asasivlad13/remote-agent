package com.agent.control.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.awt.Robot;
import java.awt.event.KeyEvent;

public class KeyboardControlService {

    private final Robot robot;

    public KeyboardControlService(Robot robot) {
        this.robot = robot;
    }

    public void press(JsonNode json) throws InterruptedException {
        if (!json.has("keyCode")) {
            return;
        }

        int keyCode = json.get("keyCode").asInt();

        pressModifiers(json);

        robot.keyPress(keyCode);
        Thread.sleep(20);
        robot.keyRelease(keyCode);

        releaseModifiers(json);
    }

    public void release(JsonNode json) {
        if (json.has("keyCode")) {
            int releaseCode = json.get("keyCode").asInt();
            robot.keyRelease(releaseCode);
        }
    }

    public void pressCtrlAltDelete() throws InterruptedException {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_DELETE);

        Thread.sleep(50);

        robot.keyRelease(KeyEvent.VK_DELETE);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    private void pressModifiers(JsonNode json) {
        if (json.has("ctrl") && json.get("ctrl").asBoolean()) {
            robot.keyPress(KeyEvent.VK_CONTROL);
        }

        if (json.has("alt") && json.get("alt").asBoolean()) {
            robot.keyPress(KeyEvent.VK_ALT);
        }

        if (json.has("shift") && json.get("shift").asBoolean()) {
            robot.keyPress(KeyEvent.VK_SHIFT);
        }
    }

    private void releaseModifiers(JsonNode json) {
        if (json.has("shift") && json.get("shift").asBoolean()) {
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }

        if (json.has("alt") && json.get("alt").asBoolean()) {
            robot.keyRelease(KeyEvent.VK_ALT);
        }

        if (json.has("ctrl") && json.get("ctrl").asBoolean()) {
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }
    }
}