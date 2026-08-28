#include "HackRFDevice.h"
#include "hackrf_android_glue.h"
#include <libusb.h>
#include <android/log.h>
#include <cstring>
#include <sstream>
#include <iomanip>
#include <stdexcept>

#define LOG_TAG "HackRfDevice"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/**
 * @brief Constructor for HackRfDevice
 */
HackRfDevice::HackRfDevice(int device_index)
    : device_(nullptr),
      device_index_(device_index),
      is_open_(false),
      frequency_(0),
      sample_rate_(0),
      lna_gain_(0),
      vga_gain_(0),
      amp_enabled_(false) {

    // Initialize the standard HackRF library
    // On Android, we must disable device discovery to avoid I/O errors due to SELinux.
    libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    int result = hackrf_init();
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to initialize libhackrf: %s", hackrf_error_name((hackrf_error)result));
    }
}

/**
 * @brief Destructor for HackRfDevice
 */
HackRfDevice::~HackRfDevice() {
    close();
    hackrf_exit();
}

/**
 * @brief Connects to a HackRF device using an Android USB file descriptor
 * @param fileDescriptor File descriptor from Java UsbDeviceConnection.getFileDescriptor()
 * @return 0 on success, negative on error
 */
int HackRfDevice::connect(int fileDescriptor) {
    if (isConnected()) return 0;
    if (fileDescriptor < 0) {
        LOGE("Invalid file descriptor: %d", fileDescriptor);
        return -1;
    }

    // 1. Ensure HackRF library and its libusb context are initialized
    // On Android, we must disable device discovery to avoid I/O errors due to SELinux.
    libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    int init_result = hackrf_init();
    if (init_result != HACKRF_SUCCESS) {
        LOGE("Failed to initialize libhackrf: %s", hackrf_error_name((hackrf_error)init_result));
        return -1;
    }

    // 2. Configure libusb for unrooted Android support
    libusb_context* ctx = hackrf_android_get_usb_context();
    if (ctx != nullptr) {
        libusb_set_option(ctx, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    } else {
        LOGE("libusb context is null after hackrf_init");
        return -1;
    }

    // 3. Wrap the system file descriptor into a libusb handle
    libusb_device_handle* usb_handle = nullptr;
    int wrap_result = libusb_wrap_sys_device(ctx, (intptr_t)fileDescriptor, &usb_handle);
    if (wrap_result < 0 || usb_handle == nullptr) {
        LOGE("Failed to wrap USB file descriptor (%d): %d", fileDescriptor, wrap_result);
        return -1;
    }

    // 4. Use the glue setup function to finish HackRF initialization
    int open_result = hackrf_android_open_setup(usb_handle, &device_);
    if (open_result != HACKRF_SUCCESS) {
        LOGE("Failed to setup HackRF device: %s", hackrf_error_name((hackrf_error)open_result));
        // Note: hackrf_android_open_setup calls libusb_close on failure
        return -1;
    }

    is_open_ = true;
    LOGI("Successfully connected to HackRF via Android file descriptor %d", fileDescriptor);
    return 0;
}

bool HackRfDevice::isConnected() const {
    return is_open_ && device_ != nullptr;
}

void HackRfDevice::close() {
    stopRx();
    stopTx();
    if (device_) {
        hackrf_close(device_);
        device_ = nullptr;
    }
    is_open_ = false;
}

SdrDeviceInfo HackRfDevice::getDeviceInfo() const {
    SdrDeviceInfo info;
    info.manufacturer = "Great Scott Gadgets";
    info.product = get_board_id_name();
    info.usbVendorId = 0x1d50;
    info.usbProductId = 0x6089;
    info.duplexMode = SdrDuplexMode::HALF_DUPLEX;

    if (isConnected()) {
        char version[64];
        if (hackrf_version_string_read(device_, version, sizeof(version)) == HACKRF_SUCCESS) {
            info.firmwareVersion = version;
        }

        read_partid_serialno_t serial;
        if (hackrf_board_partid_serialno_read(device_, &serial) == HACKRF_SUCCESS) {
            std::stringstream ss;
            for (int i = 0; i < 4; i++) {
                ss << std::hex << std::setw(8) << std::setfill('0') << serial.serial_no[i];
            }
            info.serialNumber = ss.str();
        }
    }

    return info;
}

int HackRfDevice::reset() {
    if (!isConnected()) return -1;
    return hackrf_reset(device_);
}

int HackRfDevice::setFrequency(long frequencyHz) {
    if (!isConnected()) return -1;

    int result = hackrf_set_freq(device_, static_cast<uint64_t>(frequencyHz));
    if (result == HACKRF_SUCCESS) {
        frequency_ = frequencyHz;
        return 0;
    }
    return -1;
}

long HackRfDevice::getFrequency() const {
    return frequency_.load();
}

int HackRfDevice::setSampleRate(long rateHz) {
    if (!isConnected()) return -1;

    int result = hackrf_set_sample_rate(device_, static_cast<uint32_t>(rateHz));
    if (result == HACKRF_SUCCESS) {
        sample_rate_ = rateHz;
        return 0;
    }
    return -1;
}

long HackRfDevice::getSampleRate() const {
    return sample_rate_.load();
}

int HackRfDevice::setGain(int gainDb) {
    return setVgaGain(gainDb);
}

int HackRfDevice::getGain() const {
    return getVgaGain();
}

int HackRfDevice::setLnaGain(int gainDb) {
    if (!isConnected()) return -1;
    int result = hackrf_set_lna_gain(device_, gainDb);
    if (result == HACKRF_SUCCESS) {
        lna_gain_ = gainDb;
        return 0;
    }
    return -1;
}

int HackRfDevice::getLnaGain() const {
    return lna_gain_.load();
}

int HackRfDevice::setVgaGain(int gainDb) {
    if (!isConnected()) return -1;
    int result = hackrf_set_vga_gain(device_, gainDb);
    if (result == HACKRF_SUCCESS) {
        vga_gain_ = gainDb;
        return 0;
    }
    return -1;
}

int HackRfDevice::getVgaGain() const {
    return vga_gain_.load();
}

int HackRfDevice::setAmpEnabled(bool enabled) {
    if (!isConnected()) return -1;
    int result = hackrf_set_amp_enable(device_, enabled ? 1 : 0);
    if (result == HACKRF_SUCCESS) {
        amp_enabled_ = enabled;
        return 0;
    }
    return -1;
}

bool HackRfDevice::isAmpEnabled() const {
    return amp_enabled_.load();
}

int HackRfDevice::startRx() {
    if (!isConnected()) return -1;
    int result = hackrf_start_rx(device_, rx_callback_static, this);
    return (result == HACKRF_SUCCESS) ? 0 : -1;
}

int HackRfDevice::stopRx() {
    if (!isConnected()) return -1;
    int result = hackrf_stop_rx(device_);
    return (result == HACKRF_SUCCESS) ? 0 : -1;
}

int HackRfDevice::startTx() {
    if (!isConnected()) return -1;
    int result = hackrf_start_tx(device_, tx_callback_static, this);
    return (result == HACKRF_SUCCESS) ? 0 : -1;
}

int HackRfDevice::stopTx() {
    if (!isConnected()) return -1;
    int result = hackrf_stop_tx(device_);
    return (result == HACKRF_SUCCESS) ? 0 : -1;
}

SdrStreamingState HackRfDevice::getStreamingState() const {
    if (!isConnected()) return SdrStreamingState::IDLE;

    if (hackrf_is_streaming(device_) == HACKRF_TRUE) {
        return SdrStreamingState::RX;
    }
    return SdrStreamingState::IDLE;
}

long HackRfDevice::getNativeHandle() const {
    return reinterpret_cast<long>(device_);
}

void HackRfDevice::setRxCallback(RxCallback callback) {
    std::lock_guard<std::mutex> lock(callback_mutex_);
    rx_callback_ = callback;
}

void HackRfDevice::setTxCallback(TxCallback callback) {
    std::lock_guard<std::mutex> lock(callback_mutex_);
    tx_callback_ = callback;
}

int HackRfDevice::rx_callback_static(hackrf_transfer* transfer) {
    HackRfDevice* obj = static_cast<HackRfDevice*>(transfer->rx_ctx);
    if (obj) return obj->handle_rx_callback(transfer);
    return 0;
}

int HackRfDevice::tx_callback_static(hackrf_transfer* transfer) {
    HackRfDevice* obj = static_cast<HackRfDevice*>(transfer->tx_ctx);
    if (obj) return obj->handle_tx_callback(transfer);
    return 0;
}

int HackRfDevice::handle_rx_callback(hackrf_transfer* transfer) {
    std::lock_guard<std::mutex> lock(callback_mutex_);
    if (rx_callback_) {
        rx_callback_(transfer->buffer, transfer->valid_length);
    }
    return 0;
}

int HackRfDevice::handle_tx_callback(hackrf_transfer* transfer) {
    std::lock_guard<std::mutex> lock(callback_mutex_);
    if (tx_callback_) {
        bool provided = tx_callback_(transfer->buffer, transfer->valid_length);
        return provided ? 0 : 0;
    }
    return 0;
}

std::string HackRfDevice::get_board_id_name() const {
    if (!isConnected()) return "HackRF One";
    uint8_t board_id;
    if (hackrf_board_id_read(device_, &board_id) == HACKRF_SUCCESS) {
        return hackrf_board_id_name((hackrf_board_id)board_id);
    }
    return "HackRF One";
}
