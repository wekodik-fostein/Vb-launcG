package com.winlator.xclipse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GpuDetectorTest {
    private static GpuDetector.Result c(String gl, String vk, int vendor, int driver) {
        return GpuDetector.classify(gl, vk, vendor, driver);
    }

    @Test
    public void xclipse_models_from_gl_renderer() {
        int[] verified = {530, 540, 920, 940, 950, 960};
        for (int model : verified) {
            GpuDetector.Result r = c("Samsung Xclipse " + model, "", -1, -1);
            assertEquals(GpuDetector.Vendor.XCLIPSE, r.vendor);
            assertEquals(String.valueOf(model), r.model);
            assertEquals("verified", r.modelStatus);
            assertFalse(r.inferred);
        }
    }

    @Test
    public void xclipse_550_is_listed_but_unverified() {
        GpuDetector.Result r = c("Samsung Xclipse 550", "", -1, -1);
        assertEquals(GpuDetector.Vendor.XCLIPSE, r.vendor);
        assertEquals("listed-unverified", r.modelStatus);
    }

    @Test
    public void unknown_xclipse_model_is_still_xclipse() {
        GpuDetector.Result r = c("Samsung Xclipse 999", "", -1, -1);
        assertEquals(GpuDetector.Vendor.XCLIPSE, r.vendor);
        assertEquals("unknown-model", r.modelStatus);

        GpuDetector.Result noNumber = c("Samsung Xclipse", "", -1, -1);
        assertEquals(GpuDetector.Vendor.XCLIPSE, noNumber.vendor);
        assertEquals("?", noNumber.model);
    }

    @Test
    public void xclipse_from_vulkan_device_name_when_gl_is_empty() {
        GpuDetector.Result r = c("", "Samsung Xclipse 940", 0x144D, 21);
        assertEquals(GpuDetector.Vendor.XCLIPSE, r.vendor);
        assertEquals("940", r.model);
        assertEquals("Vulkan deviceName", r.basis);
        assertFalse(r.inferred);
    }

    @Test
    public void samsung_vendor_id_or_driver_id_is_only_an_inference() {
        GpuDetector.Result byVendor = c(null, null, 0x144D, -1);
        assertEquals(GpuDetector.Vendor.XCLIPSE, byVendor.vendor);
        assertTrue(byVendor.inferred);

        GpuDetector.Result byDriver = c("", "", -1, 21);
        assertEquals(GpuDetector.Vendor.XCLIPSE, byDriver.vendor);
        assertTrue(byDriver.inferred);
    }

    @Test
    public void adreno_matches_existing_winlator_pattern() {
        assertEquals("740", c("Adreno (TM) 740", "", -1, -1).model);
        assertEquals("830", c("Adreno (TM) 830", "", -1, -1).model);
        assertEquals("630", c("Adreno (TM) 630", "", -1, -1).model);
        assertEquals(GpuDetector.Vendor.ADRENO, c("Adreno (TM) 740", "", -1, -1).vendor);
    }

    @Test
    public void mali_models() {
        GpuDetector.Result g715 = c("Mali-G715", "", -1, -1);
        assertEquals(GpuDetector.Vendor.MALI, g715.vendor);
        assertEquals("G715", g715.model);

        assertEquals("G710", c("Mali-G710 MC10", "", -1, -1).model);
        assertEquals("G78", c("ARM Mali-G78", "", -1, -1).model);
        assertEquals("T880", c("Mali-T880", "", -1, -1).model);
        assertEquals(GpuDetector.Vendor.MALI, c("", "Mali-G715", 0x13B5, -1).vendor);
    }

    @Test
    public void mali_is_never_classified_as_xclipse() {
        // Regression guard: Mali devices (e.g. Pixel/Tensor) must stay on the Mali path.
        GpuDetector.Result r = c("Mali-G710 MC10", "Mali-G710", 0x13B5, 0);
        assertEquals(GpuDetector.Vendor.MALI, r.vendor);
    }

    @Test
    public void unknown_and_null_inputs_do_not_throw() {
        assertEquals(GpuDetector.Vendor.UNKNOWN, c(null, null, -1, -1).vendor);
        assertEquals(GpuDetector.Vendor.UNKNOWN, c("", "", -1, -1).vendor);
        assertEquals(GpuDetector.Vendor.UNKNOWN, c("PowerVR DXT-48-1536", "", -1, -1).vendor);
    }
}
