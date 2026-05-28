package com.agent.control.service;

import com.agent.video.dto.ScreenInfo;
import com.fasterxml.jackson.databind.JsonNode;

import java.awt.Robot;
import java.awt.event.InputEvent;

public class MouseControlService {

    private final Robot robot;
    private final ScreenInfo screenInfo;

    public MouseControlService(Robot robot, ScreenInfo screenInfo) {
        this.robot = robot;
        this.screenInfo = screenInfo;
    }

    public void move(JsonNode json) {
        int physicalX = json.get("x").asInt();
        int physicalY = json.get("y").asInt();

        int logicalX = toSafeLogicalX(physicalX);
        int logicalY = toSafeLogicalY(physicalY);

        robot.mouseMove(logicalX, logicalY);
    }

    public void click(JsonNode json) throws InterruptedException {
        int physicalX = json.has("x") ? json.get("x").asInt() : -1;
        int physicalY = json.has("y") ? json.get("y").asInt() : -1;
        int button = json.has("button") ? json.get("button").asInt() : 1;

        if (physicalX >= 0 && physicalY >= 0) {
            robot.mouseMove(
                    toSafeLogicalX(physicalX),
                    toSafeLogicalY(physicalY)
            );
        }

        int javaButton = toJavaMouseButton(button);

        robot.mousePress(javaButton);
        Thread.sleep(50);
        robot.mouseRelease(javaButton);
    }

    public void wheel(JsonNode json) {
        if (json.has("delta")) {
            int delta = json.get("delta").asInt();
            robot.mouseWheel(delta);
        }
    }

    private int toSafeLogicalX(int physicalX) {
        int logicalX = screenInfo.toLogicalX(physicalX);
        return Math.max(0, Math.min(logicalX, screenInfo.getLogicalWidth() - 1));
    }

    private int toSafeLogicalY(int physicalY) {
        int logicalY = screenInfo.toLogicalY(physicalY);
        return Math.max(0, Math.min(logicalY, screenInfo.getLogicalHeight() - 1));
    }

    private int toJavaMouseButton(int button) {
        return switch (button) {
            case 3 -> InputEvent.BUTTON3_DOWN_MASK;
            case 2 -> InputEvent.BUTTON2_DOWN_MASK;
            default -> InputEvent.BUTTON1_DOWN_MASK;
        };
    }
}