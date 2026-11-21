package com.winlator.xclipse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only GPU classification. Pure Java (no Android classes) so it can be unit-tested on the JVM.
 * It does NOT change any Winlator behaviour: nothing in the app calls it except the diagnostics report.
 */
public final class GpuDetector {
    public enum Vendor { ADRENO, MALI, XCLIPSE, UNKNOWN }

    public static final int VENDOR_ID_SAMSUNG = 0x144D;
    /** VK_DRIVER_ID_SAMSUNG_PROPRIETARY, see app/src/main/cpp/vortekrenderer/include/vulkan/vulkan_core.h */
    public static final int VK_DRIVER_ID_SAMSUNG_PROPRIETARY = 21;

    /** Same pattern as GPUHelper.getAdrenoModelId(). */
    private static final Pattern ADRENO = Pattern.compile("adreno[^678]*([678][0-9]{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern MALI = Pattern.compile("mali[-\\s]*([a-z]?-?[0-9]{2,3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern XCLIPSE = Pattern.compile("xclipse\\s*-?\\s*([0-9]{3})", Pattern.CASE_INSENSITIVE);

    /** Models confirmed by public sources (Exynos 1480/1580/2200/2400/2500/2600). */
    private static final Set<Integer> VERIFIED_XCLIPSE = new HashSet<>(Arrays.asList(530, 540, 920, 940, 950, 960));
    /** Listed by the project owner but not confirmed by the sources we found. */
    private static final Set<Integer> UNVERIFIED_XCLIPSE = new HashSet<>(Arrays.asList(550));

    public static final class Result {
        public final Vendor vendor;
        /** Model text, e.g. "940", "G715", "740"; "?" when the vendor is only inferred. */
        public final String model;
        /** Which input decided the result. */
        public final String basis;
        /** True when decided from vendorId/driverId only (no GPU name matched). Unproven assumption. */
        public final boolean inferred;
        /** "verified", "listed-unverified", "unknown-model" or "n/a". */
        public final String modelStatus;

        Result(Vendor vendor, String model, String basis, boolean inferred, String modelStatus) {
            this.vendor = vendor;
            this.model = model;
            this.basis = basis;
            this.inferred = inferred;
            this.modelStatus = modelStatus;
        }

        @Override
        public String toString() {
            return vendor + " " + model + " (basis: " + basis + (inferred ? ", inferred" : "") + ", model: " + modelStatus + ")";
        }
    }

    private GpuDetector() {}

    /**
     * @param glRenderer   GL_RENDERER (may be null/empty)
     * @param vkDeviceName Vulkan VkPhysicalDeviceProperties.deviceName (may be null/empty)
     * @param vkVendorId   Vulkan vendorID, or -1 if unknown
     * @param vkDriverId   VkDriverId from VK_KHR_driver_properties, or -1 if unknown
     */
    public static Result classify(String glRenderer, String vkDeviceName, int vkVendorId, int vkDriverId) {
        String[] names = {glRenderer, vkDeviceName};
        String[] basis = {"GL_RENDERER", "Vulkan deviceName"};

        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (name == null || name.isEmpty()) continue;

            Matcher xclipse = XCLIPSE.matcher(name);
            if (xclipse.find()) {
                int model = Integer.parseInt(xclipse.group(1));
                String status = VERIFIED_XCLIPSE.contains(model) ? "verified"
                    : UNVERIFIED_XCLIPSE.contains(model) ? "listed-unverified" : "unknown-model";
                return new Result(Vendor.XCLIPSE, String.valueOf(model), basis[i], false, status);
            }
            if (name.toLowerCase(Locale.ENGLISH).contains("xclipse")) {
                return new Result(Vendor.XCLIPSE, "?", basis[i], false, "unknown-model");
            }

            Matcher adreno = ADRENO.matcher(name);
            if (adreno.find()) return new Result(Vendor.ADRENO, adreno.group(1), basis[i], false, "n/a");

            Matcher mali = MALI.matcher(name);
            if (mali.find()) return new Result(Vendor.MALI, mali.group(1).toUpperCase(Locale.ENGLISH), basis[i], false, "n/a");
        }

        if (vkVendorId == VENDOR_ID_SAMSUNG || vkDriverId == VK_DRIVER_ID_SAMSUNG_PROPRIETARY) {
            String why = vkVendorId == VENDOR_ID_SAMSUNG ? "Vulkan vendorId 0x144D" : "Vulkan driverId 21 (Samsung proprietary)";
            return new Result(Vendor.XCLIPSE, "?", why, true, "unknown-model");
        }
        return new Result(Vendor.UNKNOWN, "?", "no match", false, "n/a");
    }
}
