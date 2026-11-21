package com.winlator.xclipse;

/**
 * Read-only Vulkan probe (system libvulkan.so). Runs only when the diagnostics report is opened.
 * Never throws: any failure is reported as "probe=error:..." in the returned VulkanInfo.
 */
public final class VulkanCapabilityProbe {
    static {
        try {
            System.loadLibrary("winlator");
        }
        catch (Throwable ignored) {}
    }

    private VulkanCapabilityProbe() {}

    private static native String nativeProbe();

    public static VulkanInfo probe() {
        try {
            return VulkanInfo.parse(nativeProbe());
        }
        catch (Throwable t) {
            return VulkanInfo.parse("probe=error:" + t.getClass().getSimpleName() + "\n");
        }
    }
}
