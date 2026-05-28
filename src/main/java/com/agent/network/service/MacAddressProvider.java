package com.agent.network.service;

import java.net.NetworkInterface;
import java.util.Enumeration;

public class MacAddressProvider {

    private static final String FALLBACK_MAC = "AA:BB:CC:DD:EE:FF";

    private MacAddressProvider() {
    }

    public static String getMacAddress() {
        String macFromConfig = System.getProperty("pc.mac");

        if (macFromConfig != null && !macFromConfig.isEmpty()) {
            System.out.println("  Using MAC from config: " + macFromConfig);
            return macFromConfig;
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();

                if (mac != null && mac.length == 6) {
                    String detectedMac = formatMac(mac);

                    if (!detectedMac.equals("00:00:00:00:00:00")) {
                        System.out.println("  Auto-detected MAC: " + detectedMac);
                        return detectedMac;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not get MAC: " + e.getMessage());
        }

        System.out.println("  Using fallback MAC: " + FALLBACK_MAC);
        return FALLBACK_MAC;
    }

    private static String formatMac(byte[] mac) {
        StringBuilder sb = new StringBuilder();

        for (byte b : mac) {
            sb.append(String.format("%02X", b)).append(":");
        }

        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }
}