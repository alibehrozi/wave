#pragma once

#include "hardware/sdr.h"
#include <hackrf.h>
#include <memory>
#include <string>
#include <atomic>
#include <mutex>
#include <vector>
#include <functional>

/**
 * @class HackRfDevice
 * @brief HackRF SDR device implementation
 */
class HackRfDevice : public SdrDevice {
public:
    using sptr = std::shared_ptr<HackRfDevice>;

    explicit HackRfDevice(int device_index = 0);
    ~HackRfDevice() override;

    // Sdr interface implementation
    bool isConnected() const override;
    void close() override;
    SdrDeviceInfo getDeviceInfo() const override;
    int reset() override;
    int setFrequency(long frequencyHz) override;
    long getFrequency() const override;
    int setSampleRate(long rateHz) override;
    long getSampleRate() const override;
    int setGain(int gainDb) override;
    int getGain() const override;
    int startRx() override;
    int stopRx() override;
    int startTx() override;
    int stopTx() override;
    SdrStreamingState getStreamingState() const override;
    long getNativeHandle() const override;

    // Callbacks for internal DSP blocks
    void setRxCallback(RxCallback callback) override;
    void setTxCallback(TxCallback callback) override;

    // HackRF Specific methods (matching HackRfNative.java)
    int setLnaGain(int gainDb);
    int getLnaGain() const;
    int setVgaGain(int gainDb);
    int getVgaGain() const;
    int setAmpEnabled(bool enabled);
    bool isAmpEnabled() const;

    // Android-specific connection method
    int connect(int fileDescriptor);

private:
    hackrf_device* device_;
    int device_index_;
    std::atomic<bool> is_open_;

    std::atomic<long> frequency_;
    std::atomic<long> sample_rate_;
    std::atomic<int> lna_gain_;
    std::atomic<int> vga_gain_;
    std::atomic<bool> amp_enabled_;

    mutable std::mutex callback_mutex_;
    RxCallback rx_callback_;
    TxCallback tx_callback_;

    // Static callback handlers for libhackrf
    static int rx_callback_static(hackrf_transfer* transfer);
    static int tx_callback_static(hackrf_transfer* transfer);

    // Internal callback handlers
    int handle_rx_callback(hackrf_transfer* transfer);
    int handle_tx_callback(hackrf_transfer* transfer);

    std::string get_board_id_name() const;
};
