package com.winlator.xclipse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VulkanInfoTest {
    private static final String SAMPLE =
        "probe=ok\n" +
        "instanceApi=4198400\n" +
        "deviceCount=1\n" +
        "dev0.name=Samsung Xclipse 940\n" +
        "dev0.vendorId=0x144d\n" +
        "dev0.deviceId=0x1234\n" +
        "dev0.apiVersion=4206721\n" +
        "dev0.driverId=21\n" +
        "dev0.extCount=3\n" +
        "dev0.exts=VK_KHR_swapchain,VK_KHR_driver_properties,VK_EXT_robustness2\n";

    @Test
    public void parses_sample_output() {
        VulkanInfo info = VulkanInfo.parse(SAMPLE);
        assertTrue(info.isOk());
        assertEquals(1, info.deviceCount());
        assertEquals("Samsung Xclipse 940", info.deviceName(0));
        assertEquals(0x144D, info.vendorId(0));
        assertEquals(21, info.driverId(0));
        assertEquals(3, info.extensions(0).size());
        assertTrue(info.extensions(0).contains("VK_KHR_driver_properties"));
    }

    @Test
    public void error_and_garbage_are_safe() {
        assertFalse(VulkanInfo.parse("probe=error:dlopen\n").isOk());
        assertFalse(VulkanInfo.parse(null).isOk());
        assertFalse(VulkanInfo.parse("garbage without equals").isOk());
        assertEquals(-1, VulkanInfo.parse("").vendorId(0));
        assertEquals(-1, VulkanInfo.parse("").driverId(0));
    }

    @Test
    public void version_helpers() {
        int v13 = (1 << 22) | (3 << 12) | 128;
        assertEquals("1.3.128", VulkanInfo.formatVersion(v13));
        assertTrue(VulkanInfo.isAtLeast(v13, 1, 3));
        assertTrue(VulkanInfo.isAtLeast(v13, 1, 1));
        assertFalse(VulkanInfo.isAtLeast((1 << 22) | (2 << 12), 1, 3));
        assertEquals("n/a", VulkanInfo.formatVersion(0));
    }
}
