package com.agent.control.service;

import com.agent.AgentApplication;
import com.agent.ui.service.BlackoutWindow;
import com.agent.video.service.GStreamerManager;

public class PowerControlService {

    public void activateSoftSleep() {
        try {
            System.out.println("Получена команда умного сна");

            stopVideoStream();

            Thread.sleep(1000);

            runCommand("powercfg /setactive SCHEME_MIN");

            limitCpuForSleep();
            enableUsbSelectiveSuspend();
            muteSound();

            setBrightness(0);

            Thread.sleep(500);

            BlackoutWindow.showBlackout();

            System.out.println("✓ Smart sleep: blackout enabled, brightness 0, video stopped, sound muted, CPU limited, power saver enabled");

        } catch (Exception e) {
            System.err.println("Ошибка умного сна: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void deactivateSoftSleep() {
        try {
            System.out.println("Получена команда умного пробуждения");

            BlackoutWindow.hideBlackout();

            Thread.sleep(500);

            setBrightness(70);

            runCommand("powercfg /setactive SCHEME_BALANCED");

            restoreCpuAfterWake();
            disableUsbSelectiveSuspend();
            unmuteSound();

            Thread.sleep(500);

            startVideoStream();

            System.out.println("✓ Smart wake: blackout disabled, brightness restored, sound enabled, CPU restored, video restarted");

        } catch (Exception e) {
            System.err.println("Ошибка умного пробуждения: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runCommand(String command) throws Exception {
        new ProcessBuilder("cmd.exe", "/c", command)
                .redirectErrorStream(true)
                .start();
    }

    private void runPowerShell(String script) throws Exception {
        new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script
        ).redirectErrorStream(true).start();
    }

    private void setBrightness(int value) {
        try {
            String script =
                    "$brightness = Get-WmiObject -Namespace root/WMI -Class WmiMonitorBrightnessMethods; " +
                            "if ($brightness -ne $null) { $brightness.WmiSetBrightness(1, " + value + "); }";

            runPowerShell(script);
        } catch (Exception e) {
            System.err.println("Brightness change failed: " + e.getMessage());
        }
    }

    private void enableUsbSelectiveSuspend() {
        try {
            runCommand("powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_USB USBSELECTIVE SUSPEND 1");
            runCommand("powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_USB USBSELECTIVE SUSPEND 1");
            runCommand("powercfg /SETACTIVE SCHEME_CURRENT");
            System.out.println("✓ USB selective suspend enabled");
        } catch (Exception e) {
            System.err.println("USB selective suspend enable failed: " + e.getMessage());
        }
    }

    private void disableUsbSelectiveSuspend() {
        try {
            runCommand("powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_USB USBSELECTIVE SUSPEND 0");
            runCommand("powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_USB USBSELECTIVE SUSPEND 0");
            runCommand("powercfg /SETACTIVE SCHEME_CURRENT");
            System.out.println("✓ USB selective suspend disabled");
        } catch (Exception e) {
            System.err.println("USB selective suspend disable failed: " + e.getMessage());
        }
    }

    private void limitCpuForSleep() {
        try {
            runCommand("powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 30");
            runCommand("powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 30");
            runCommand("powercfg /SETACTIVE SCHEME_CURRENT");
            System.out.println("✓ CPU limited to 30%");
        } catch (Exception e) {
            System.err.println("CPU limit failed: " + e.getMessage());
        }
    }

    private void restoreCpuAfterWake() {
        try {
            runCommand("powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 100");
            runCommand("powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 100");
            runCommand("powercfg /SETACTIVE SCHEME_CURRENT");
            System.out.println("✓ CPU restored to 100%");
        } catch (Exception e) {
            System.err.println("CPU restore failed: " + e.getMessage());
        }
    }

    private void muteSound() {
        try {
            runCommand("C:\\Windows\\nircmd.exe mutesysvolume 1");
            System.out.println("✓ Sound muted");
        } catch (Exception e) {
            System.err.println("Mute sound failed: " + e.getMessage());
        }
    }

    private void unmuteSound() {
        try {
            runCommand("C:\\Windows\\nircmd.exe mutesysvolume 0");
            System.out.println("✓ Sound unmuted");
        } catch (Exception e) {
            System.err.println("Unmute sound failed: " + e.getMessage());
        }
    }

    private void stopVideoStream() {
        try {
            GStreamerManager gstreamerManager = AgentApplication.getGStreamerManager();

            if (gstreamerManager != null && gstreamerManager.isRunning()) {
                gstreamerManager.stop();
                System.out.println("✓ Video stream stopped");
            }
        } catch (Exception e) {
            System.err.println("Video stream stop failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startVideoStream() {
        try {
            GStreamerManager gstreamerManager = AgentApplication.getGStreamerManager();

            if (gstreamerManager != null && !gstreamerManager.isRunning()) {
                gstreamerManager.start();
                System.out.println("✓ Video stream started");
            }
        } catch (Exception e) {
            System.err.println("Video stream start failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}