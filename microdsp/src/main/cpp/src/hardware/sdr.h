#ifndef SDR_H
#define SDR_H

#include <string>
#include <functional>
#include <memory>

/**
 * Represents the duplex mode and transmission/reception capabilities of an SDR device.
 */
enum class SdrDuplexMode {
    RX_ONLY,
    TX_ONLY,
    HALF_DUPLEX,
    FULL_DUPLEX
};

/**
 * Represents the current streaming state of an SDR device.
 */
enum class SdrStreamingState {
    IDLE,
    RX,
    TX,
    RX_TX
};

/**
 * Provides static identification and hardware information for an SDR device.
 */
struct SdrDeviceInfo {
    std::string manufacturer;
    std::string product;
    std::string hardwareRevision;
    std::string firmwareVersion;
    std::string serialNumber;
    int usbVendorId{0};
    int usbProductId{0};
    SdrDuplexMode duplexMode{SdrDuplexMode::HALF_DUPLEX};
};

/**
 * Core SDR device interface aligned with Java Sdr.java
 */
class SdrDevice {
public:
    using sptr = std::shared_ptr<SdrDevice>;

    virtual ~SdrDevice() = default;

    /**
     * Checks whether the SDR is currently connected and usable.
     */
    virtual bool isConnected() const = 0;

    /**
     * Closes the SDR and releases all associated resources.
     */
    virtual void close() = 0;

    /**
     * Returns static identification and hardware information about the SDR.
     */
    virtual SdrDeviceInfo getDeviceInfo() const = 0;

    /**
     * Performs a hardware reset.
     */
    virtual int reset() = 0;

    /**
     * Sets the center frequency.
     */
    virtual int setFrequency(long frequencyHz) = 0;

    /**
     * Gets the currently configured center frequency.
     */
    virtual long getFrequency() const = 0;

    /**
     * Sets the sample rate.
     */
    virtual int setSampleRate(long rateHz) = 0;

    /**
     * Gets the currently configured sample rate.
     */
    virtual long getSampleRate() const = 0;

    /**
     * Sets the SDR gain.
     */
    virtual int setGain(int gainDb) = 0;

    /**
     * Gets the currently configured SDR gain.
     */
    virtual int getGain() const = 0;

    /**
     * Starts receiving IQ samples from the SDR.
     */
    virtual int startRx() = 0;

    /**
     * Stops receiving IQ samples from the SDR.
     */
    virtual int stopRx() = 0;

    /**
     * Starts transmitting IQ samples to the SDR.
     */
    virtual int startTx() = 0;

    /**
     * Stops transmitting IQ samples to the SDR.
     */
    virtual int stopTx() = 0;

    /**
     * Returns the current RX/TX streaming state.
     */
    virtual SdrStreamingState getStreamingState() const = 0;

    /**
     * Returns the native handle associated with this SDR.
     */
    virtual long getNativeHandle() const = 0;

    // --- Capabilities ---

    virtual SdrDuplexMode getDuplexMode() const {
        return getDeviceInfo().duplexMode;
    }

    virtual bool supportsRx() const {
        SdrDuplexMode mode = getDuplexMode();
        return mode == SdrDuplexMode::RX_ONLY ||
               mode == SdrDuplexMode::HALF_DUPLEX ||
               mode == SdrDuplexMode::FULL_DUPLEX;
    }

    virtual bool supportsTx() const {
        SdrDuplexMode mode = getDuplexMode();
        return mode == SdrDuplexMode::TX_ONLY ||
               mode == SdrDuplexMode::HALF_DUPLEX ||
               mode == SdrDuplexMode::FULL_DUPLEX;
    }

    virtual bool isFullDuplex() const {
        return getDuplexMode() == SdrDuplexMode::FULL_DUPLEX;
    }

    virtual bool isHalfDuplex() const {
        return getDuplexMode() == SdrDuplexMode::HALF_DUPLEX;
    }

    // --- Internal C++ Callback Support (Required for DSP blocks) ---

    using RxCallback = std::function<void(const uint8_t *data, size_t length)>;
    using TxCallback = std::function<bool(uint8_t *buffer, size_t length)>;

    virtual void setRxCallback(RxCallback callback) = 0;
    virtual void setTxCallback(TxCallback callback) = 0;
};

#endif // SDR_H
