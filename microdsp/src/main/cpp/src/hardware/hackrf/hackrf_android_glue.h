#pragma once

#include <stdint.h>
#include <stddef.h>
#include <libusb.h>
#include <hackrf.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Provides access to the internal libusb context used by libhackrf.
 * This is necessary for Android to perform libusb_set_option and libusb_wrap_sys_device.
 */
libusb_context* hackrf_android_get_usb_context();

/**
 * @brief Wraps the internal libhackrf device setup logic.
 * This function completes the initialization of a hackrf_device from an
 * already opened libusb_device_handle.
 */
int hackrf_android_open_setup(libusb_device_handle* usb_device, hackrf_device** device);

#ifdef __cplusplus
}
#endif
