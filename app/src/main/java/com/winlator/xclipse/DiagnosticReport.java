package com.winlator.xclipse;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.winlator.container.GraphicsDrivers;
import com.winlator.core.DefaultVersion;
import com.winlator.core.GPUHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Builds the phase-1 diagnostics text. STRICTLY READ-ONLY: it calls only getters of existing Winlator code
 * and never changes containers, drivers, settings or the graphics path.
 * Must be called from a background thread (GPUHelper.glGetRenderer may create a temporary EGL context).
 */
public final class DiagnosticReport {
    private DiagnosticReport() {}

    public static String build(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Xclipse diagnostics (phase 1, read-only) ===\n");

        // --- App / device ---
        String versionName = "?";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = info.versionName;
        }
        catch (Exception ignored) {}
        sb.append("Winlator fork version: ").append(versionName).append('\n');
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        String soc = Build.HARDWARE + " / " + Build.BOARD;
        if (Build.VERSION.SDK_INT >= 31) soc = Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL + " (" + soc + ")";
        sb.append("SoC: ").append(soc).append('\n');
        sb.append("ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');

        ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        sb.append(String.format(Locale.US, "RAM: total %.1f GB, available %.1f GB\n", mem.totalMem / 1073741824.0, mem.availMem / 1073741824.0));

        // --- OpenGL ES (existing Winlator helper, cached) ---
        String glRenderer = nullToEmpty(GPUHelper.glGetRenderer(context));
        String glVendor = nullToEmpty(GPUHelper.glGetVendor(context));
        String glVersion = nullToEmpty(GPUHelper.glGetVersion(context));
        sb.append("\n-- OpenGL ES --\n");
        sb.append("GL_RENDERER: ").append(glRenderer).append('\n');
        sb.append("GL_VENDOR: ").append(glVendor).append('\n');
        sb.append("GL_VERSION: ").append(glVersion).append('\n');

        // --- Vulkan (system libvulkan.so) ---
        VulkanInfo vk = VulkanCapabilityProbe.probe();
        sb.append("\n-- Vulkan (system libvulkan.so) --\n");
        sb.append("probe: ").append(vk.status()).append('\n');
        String vkName = "";
        int vkVendor = -1;
        int vkDriverId = -1;
        int vkApi = 0;
        if (vk.isOk()) {
            sb.append("instance API: ").append(VulkanInfo.formatVersion(vk.instanceApiVersion())).append('\n');
            sb.append("devices: ").append(vk.deviceCount()).append(" (device 0 is used below, as in Winlator's own code)\n");
            vkName = vk.deviceName(0);
            vkVendor = vk.vendorId(0);
            vkDriverId = vk.driverId(0);
            vkApi = vk.apiVersion(0);
            sb.append("deviceName: ").append(vkName).append('\n');
            sb.append("vendorId: ").append(vk.get("dev0.vendorId")).append("  deviceId: ").append(vk.get("dev0.deviceId")).append('\n');
            sb.append("apiVersion: ").append(VulkanInfo.formatVersion(vkApi)).append('\n');
            sb.append("driverVersion (raw): ").append(vk.get("dev0.driverVersion")).append('\n');
            sb.append("driverId: ").append(vkDriverId >= 0 ? String.valueOf(vkDriverId) : "n/a (VK_KHR_driver_properties not available)");
            if (vkDriverId == GpuDetector.VK_DRIVER_ID_SAMSUNG_PROPRIETARY) sb.append(" (SAMSUNG_PROPRIETARY)");
            sb.append('\n');
            sb.append("driverName: ").append(vk.get("dev0.driverName")).append('\n');
            sb.append("driverInfo: ").append(vk.get("dev0.driverInfo")).append('\n');
            sb.append("maxMemoryAllocationCount: ").append(vk.get("dev0.maxMemoryAllocationCount")).append('\n');
            int heaps = 0;
            try { heaps = Integer.parseInt(vk.get("dev0.heapCount")); } catch (NumberFormatException ignored) {}
            for (int h = 0; h < heaps; h++) sb.append("heap").append(h).append(": ").append(vk.get("dev0.heap" + h)).append('\n');
            List<String> exts = vk.extensions(0);
            sb.append("extensions (").append(exts.size()).append("):\n");
            for (String ext : exts) sb.append("  ").append(ext).append('\n');
        }

        // --- Classification ---
        GpuDetector.Result cls = GpuDetector.classify(glRenderer, vkName, vkVendor, vkDriverId);
        sb.append("\n-- Classification --\n");
        sb.append("vendor: ").append(cls.vendor).append('\n');
        sb.append("model: ").append(cls.model).append('\n');
        sb.append("basis: ").append(cls.basis).append('\n');
        sb.append("inferred (unproven): ").append(cls.inferred).append('\n');
        sb.append("model status: ").append(cls.modelStatus).append('\n');

        // --- Facts about what Winlator itself would do (copied from existing code, nothing is applied) ---
        String defaultDriver = GraphicsDrivers.getDefaultDriver(context);
        String vulkanDefault = defaultDriver.split(",")[0];
        sb.append("\n-- Winlator behaviour on this device (read-only) --\n");
        sb.append("default graphics driver (GraphicsDrivers.getDefaultDriver): ").append(defaultDriver).append('\n');
        if (GraphicsDrivers.VORTEK.equals(vulkanDefault) && vk.isOk()) {
            boolean api13 = VulkanInfo.isAtLeast(vkApi, 1, 3);
            String note = api13
                ? " (API >= 1.3; Winlator also checks a feature list, so it may still pick " + DefaultVersion.MINOR_DXVK + ")"
                : " (API < 1.3)";
            sb.append("DXVK by DefaultVersion rule: ")
              .append(api13 ? DefaultVersion.MAJOR_DXVK : DefaultVersion.MINOR_DXVK)
              .append(note).append('\n');
        }
        boolean maliPath = vkName.contains("Mali");
        sb.append("Vortek Mali workarounds active (deviceName contains \"Mali\"): ").append(maliPath).append('\n');
        sb.append("custom Vulkan driver import: not touched in phase 1\n");

        sb.append("\nNo settings were changed by this report.\n");
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
