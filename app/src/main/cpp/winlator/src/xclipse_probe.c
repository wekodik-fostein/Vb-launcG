// Xclipse diagnostics, phase 1: READ-ONLY Vulkan capability probe.
// Mirrors the pattern of GPUHelper.vkGetDeviceExtensions() in gpu_helper.c (dlopen the system libvulkan,
// create a throw-away instance, query, destroy). Called only on demand from the diagnostics report.
#include <jni.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>

#define VK_NO_PROTOTYPES 1
#include <vulkan/vulkan.h>

#include "winlator.h"

#define XC_STYPE_PHYSICAL_DEVICE_PROPERTIES_2 1000059001
#define XC_STYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES 1000196000
#define XC_MAX_DEVICES 4

// Local copies of the Vulkan 1.2 structs (layout fixed by the specification) so we do not depend on NDK header version.
typedef struct {
    uint32_t sType;
    void* pNext;
    uint32_t driverID;
    char driverName[256];
    char driverInfo[256];
    uint8_t conformanceVersion[4];
} XcDriverProps;

typedef struct {
    uint32_t sType;
    void* pNext;
    VkPhysicalDeviceProperties properties;
} XcProps2;

typedef void (VKAPI_PTR *XcPFN_GetProps2)(VkPhysicalDevice, XcProps2*);

typedef struct {
    char* data;
    size_t len;
    size_t cap;
} XcBuf;

static void xcAppend(XcBuf* b, const char* fmt, ...) {
    va_list ap, ap2;
    va_start(ap, fmt);
    va_copy(ap2, ap);
    int n = vsnprintf(NULL, 0, fmt, ap);
    va_end(ap);
    if (n < 0) {
        va_end(ap2);
        return;
    }
    size_t need = b->len + (size_t)n + 1;
    if (need > b->cap) {
        size_t ncap = b->cap ? b->cap : 4096;
        while (ncap < need) ncap *= 2;
        char* nd = realloc(b->data, ncap);
        if (!nd) {
            va_end(ap2);
            return;
        }
        b->data = nd;
        b->cap = ncap;
    }
    vsnprintf(b->data + b->len, (size_t)n + 1, fmt, ap2);
    va_end(ap2);
    b->len += (size_t)n;
}

// Driver-provided strings may contain anything; NewStringUTF needs valid (modified) UTF-8, so keep plain ASCII only.
static void xcSanitize(char* s) {
    for (; s && *s; s++) {
        unsigned char c = (unsigned char)*s;
        if (c != '\n' && (c < 0x20 || c >= 0x7f)) *s = '?';
    }
}

JNIEXPORT jstring JNICALL
Java_com_winlator_xclipse_VulkanCapabilityProbe_nativeProbe(JNIEnv *env, jclass cls) {
    XcBuf out = {0};
    void* libvulkan = NULL;
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice* devices = NULL;
    VkExtensionProperties* exts = NULL;
    PFN_vkDestroyInstance destroyInstance = NULL;

    libvulkan = dlopen(LIBVULKAN_PATH, RTLD_NOW | RTLD_LOCAL);
    if (!libvulkan) {
        xcAppend(&out, "probe=error:dlopen\n");
        goto done;
    }

    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance)dlsym(libvulkan, "vkCreateInstance");
    destroyInstance = (PFN_vkDestroyInstance)dlsym(libvulkan, "vkDestroyInstance");
    PFN_vkEnumeratePhysicalDevices enumDevices = (PFN_vkEnumeratePhysicalDevices)dlsym(libvulkan, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties getProps = (PFN_vkGetPhysicalDeviceProperties)dlsym(libvulkan, "vkGetPhysicalDeviceProperties");
    PFN_vkGetPhysicalDeviceMemoryProperties getMemProps = (PFN_vkGetPhysicalDeviceMemoryProperties)dlsym(libvulkan, "vkGetPhysicalDeviceMemoryProperties");
    PFN_vkEnumerateDeviceExtensionProperties enumExts = (PFN_vkEnumerateDeviceExtensionProperties)dlsym(libvulkan, "vkEnumerateDeviceExtensionProperties");
    PFN_vkEnumerateInstanceVersion enumInstanceVersion = (PFN_vkEnumerateInstanceVersion)dlsym(libvulkan, "vkEnumerateInstanceVersion");
    XcPFN_GetProps2 getProps2 = (XcPFN_GetProps2)dlsym(libvulkan, "vkGetPhysicalDeviceProperties2");

    if (!createInstance || !destroyInstance || !enumDevices || !getProps || !getMemProps || !enumExts) {
        xcAppend(&out, "probe=error:symbols\n");
        goto done;
    }

    uint32_t instanceVersion = VK_MAKE_VERSION(1, 0, 0);
    if (enumInstanceVersion) enumInstanceVersion(&instanceVersion);
    int canUseProps2 = getProps2 && instanceVersion >= VK_MAKE_VERSION(1, 1, 0);

    VkApplicationInfo appInfo = {0};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.apiVersion = canUseProps2 ? VK_MAKE_VERSION(1, 1, 0) : VK_MAKE_VERSION(1, 0, 0);

    VkInstanceCreateInfo createInfo = {0};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    if (createInstance(&createInfo, NULL, &instance) != VK_SUCCESS) {
        instance = VK_NULL_HANDLE;
        xcAppend(&out, "probe=error:create-instance\n");
        goto done;
    }

    uint32_t deviceCount = 0;
    VkResult r = enumDevices(instance, &deviceCount, NULL);
    if (r != VK_SUCCESS || deviceCount == 0) {
        xcAppend(&out, "probe=error:no-devices\n");
        goto done;
    }
    if (deviceCount > XC_MAX_DEVICES) deviceCount = XC_MAX_DEVICES;

    devices = calloc(deviceCount, sizeof(VkPhysicalDevice));
    if (!devices) {
        xcAppend(&out, "probe=error:oom\n");
        goto done;
    }
    r = enumDevices(instance, &deviceCount, devices);
    if (r != VK_SUCCESS && r != VK_INCOMPLETE) {
        xcAppend(&out, "probe=error:enumerate\n");
        goto done;
    }

    xcAppend(&out, "probe=ok\n");
    xcAppend(&out, "instanceApi=%u\n", instanceVersion);
    xcAppend(&out, "deviceCount=%u\n", deviceCount);

    for (uint32_t i = 0; i < deviceCount; i++) {
        VkPhysicalDeviceProperties props = {0};
        getProps(devices[i], &props);

        xcAppend(&out, "dev%u.name=%s\n", i, props.deviceName);
        xcAppend(&out, "dev%u.vendorId=0x%x\n", i, props.vendorID);
        xcAppend(&out, "dev%u.deviceId=0x%x\n", i, props.deviceID);
        xcAppend(&out, "dev%u.type=%d\n", i, (int)props.deviceType);
        xcAppend(&out, "dev%u.apiVersion=%u\n", i, props.apiVersion);
        xcAppend(&out, "dev%u.driverVersion=%u\n", i, props.driverVersion);
        xcAppend(&out, "dev%u.maxMemoryAllocationCount=%u\n", i, props.limits.maxMemoryAllocationCount);

        // Extensions first: VK_KHR_driver_properties decides whether we may chain the driver-properties struct.
        uint32_t extCount = 0;
        int hasDriverProps = 0;
        if (enumExts(devices[i], NULL, &extCount, NULL) == VK_SUCCESS && extCount > 0) {
            exts = calloc(extCount, sizeof(VkExtensionProperties));
            if (exts && enumExts(devices[i], NULL, &extCount, exts) == VK_SUCCESS) {
                xcAppend(&out, "dev%u.extCount=%u\n", i, extCount);
                xcAppend(&out, "dev%u.exts=", i);
                for (uint32_t e = 0; e < extCount; e++) {
                    if (strcmp(exts[e].extensionName, "VK_KHR_driver_properties") == 0) hasDriverProps = 1;
                    xcAppend(&out, "%s%s", e ? "," : "", exts[e].extensionName);
                }
                xcAppend(&out, "\n");
            }
            free(exts);
            exts = NULL;
        }

        if (canUseProps2 && (hasDriverProps || props.apiVersion >= VK_MAKE_VERSION(1, 2, 0))) {
            XcDriverProps driver;
            memset(&driver, 0, sizeof(driver));
            driver.sType = XC_STYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES;

            XcProps2 props2;
            memset(&props2, 0, sizeof(props2));
            props2.sType = XC_STYPE_PHYSICAL_DEVICE_PROPERTIES_2;
            props2.pNext = &driver;

            getProps2(devices[i], &props2);
            driver.driverName[sizeof(driver.driverName) - 1] = 0;
            driver.driverInfo[sizeof(driver.driverInfo) - 1] = 0;
            xcAppend(&out, "dev%u.driverId=%u\n", i, driver.driverID);
            xcAppend(&out, "dev%u.driverName=%s\n", i, driver.driverName);
            xcAppend(&out, "dev%u.driverInfo=%s\n", i, driver.driverInfo);
        }

        VkPhysicalDeviceMemoryProperties mem;
        memset(&mem, 0, sizeof(mem));
        getMemProps(devices[i], &mem);
        xcAppend(&out, "dev%u.heapCount=%u\n", i, mem.memoryHeapCount);
        for (uint32_t h = 0; h < mem.memoryHeapCount && h < VK_MAX_MEMORY_HEAPS; h++) {
            xcAppend(&out, "dev%u.heap%u=size:%llu flags:%u\n", i, h,
                     (unsigned long long)mem.memoryHeaps[h].size, (unsigned)mem.memoryHeaps[h].flags);
        }
    }

done:
    if (instance && destroyInstance) destroyInstance(instance, NULL);
    if (devices) free(devices);
    if (exts) free(exts);
    if (libvulkan) dlclose(libvulkan);

    if (out.data) xcSanitize(out.data);
    jstring result = (*env)->NewStringUTF(env, out.data ? out.data : "probe=error:oom\n");
    if (out.data) free(out.data);
    return result;
}
