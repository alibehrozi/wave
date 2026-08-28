#include "hackrf_android_glue.h"

/**
 * Inclusion of the original libhackrf implementation file.
 * This allows us to access symbols declared 'static' (private) within hackrf.c
 * without modifying the original library source files.
 */
#include "../../../external/libhackrf/host/libhackrf/src/hackrf.c"

libusb_context* hackrf_android_get_usb_context() {
    return g_libusb_context;
}

int hackrf_android_open_setup(libusb_device_handle* usb_device, hackrf_device** device) {
    return hackrf_open_setup(usb_device, device);
}
