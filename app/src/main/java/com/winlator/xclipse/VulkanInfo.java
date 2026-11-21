package com.winlator.xclipse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed output of the native probe ("key=value" lines). Pure Java. */
public final class VulkanInfo {
    private final Map<String, String> values = new LinkedHashMap<>();

    private VulkanInfo() {}

    public static VulkanInfo parse(String text) {
        VulkanInfo info = new VulkanInfo();
        if (text == null) {
            info.values.put("probe", "error:null");
            return info;
        }
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            info.values.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        if (!info.values.containsKey("probe")) info.values.put("probe", "error:no-output");
        return info;
    }

    public String get(String key) {
        String value = values.get(key);
        return value != null ? value : "";
    }

    public boolean isOk() {
        return "ok".equals(get("probe")) && deviceCount() > 0;
    }

    public String status() {
        return get("probe");
    }

    public int deviceCount() {
        return parseInt(get("deviceCount"), 0);
    }

    public String deviceName(int index) {
        return get("dev" + index + ".name");
    }

    /** Vulkan vendorID or -1. */
    public int vendorId(int index) {
        return parseHex(get("dev" + index + ".vendorId"));
    }

    public int driverId(int index) {
        return parseInt(get("dev" + index + ".driverId"), -1);
    }

    public int apiVersion(int index) {
        return parseInt(get("dev" + index + ".apiVersion"), 0);
    }

    public int instanceApiVersion() {
        return parseInt(get("instanceApi"), 0);
    }

    public List<String> extensions(int index) {
        List<String> list = new ArrayList<>();
        String exts = get("dev" + index + ".exts");
        if (exts.isEmpty()) return list;
        for (String ext : exts.split(",")) {
            if (!ext.isEmpty()) list.add(ext);
        }
        return list;
    }

    public Map<String, String> all() {
        return values;
    }

    /** Decodes a packed Vulkan version (major.minor.patch). */
    public static String formatVersion(int packed) {
        if (packed <= 0) return "n/a";
        return (packed >>> 22) + "." + ((packed >> 12) & 0x3FF) + "." + (packed & 0xFFF);
    }

    /** True when major.minor of the packed version is at least the requested one. */
    public static boolean isAtLeast(int packed, int major, int minor) {
        int vMajor = packed >>> 22;
        int vMinor = (packed >> 12) & 0x3FF;
        return vMajor > major || (vMajor == major && vMinor >= minor);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseHex(String s) {
        try {
            String t = s.trim().toLowerCase();
            if (t.startsWith("0x")) t = t.substring(2);
            return t.isEmpty() ? -1 : (int)Long.parseLong(t, 16);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }
}
