#pragma once

#include <memory>
#include <mutex>
#include <atomic>
#include <vector>
#include <condition_variable>
#include "core/Block.h"
#include "hardware/sdr.h"

/**
 * @class SdrSource
 * @brief SDR input block that receives IQ samples from an SDR device.
 *
 * This block receives IQ samples (complex float or complex double) from
 * the SDR hardware and outputs them to the DSP pipeline.
 *
 * The SDR device should be fully configured (frequency, gain, sample rate)
 * before being passed to this block. Configuration is done directly through
 * the Sdr interface, not through this block.
 *
 * This block is passive - starting and stopping the flow of samples from
 * the device to the processing pipeline.
 */
class SdrSource : public Block {
public:
    using sptr = std::shared_ptr<SdrSource>;

    /**
     * @brief Create an SdrSource
     * @param device SDR device instance (must already be configured)
     * @param data_type IQ data type (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param name Block name
     */
    SdrSource(std::shared_ptr<SdrDevice> device,
              const std::string& name = "sdr_source");

    /**
     * @brief Factory method
     */
    static sptr make(std::shared_ptr<SdrDevice> device,
                     const std::string& name = "sdr_source") {
        return std::make_shared<SdrSource>(device, name);
    }

    ~SdrSource() override;

    /**
     * @brief Process incoming IQ data
     *
     * This method is called by the pipeline/scheduler to process data.
     * It reads from the SDR device and writes to the output port.
     */
    void work() override;

    /**
     * @brief Create native block for JNI
     * @param name Block name
     * @return Native handle
     */
    int64_t nativeCreateBlock(const std::string& name) override {
        return reinterpret_cast<long>(this);
    }

    /**
     * @brief Start the block processing
     * @return 0 on success, negative on error
     */
    int start() override;

    /**
     * @brief Stop the block processing
     */
    void stop() override;

    /**
     * @brief Returns the underlying SDR device.
     * Use this to configure frequency, gain, sample rate, etc.
     * @return The SDR device instance
     */
    std::shared_ptr<SdrDevice> getDevice() const {
        return device_;
    }

    /**
     * @brief Checks if the device is currently receiving samples.
     * @return true if receiving, false otherwise
     */
    bool isReceiving() const {
        auto state = device_->getStreamingState();
        return state == SdrStreamingState::RX || state == SdrStreamingState::RX_TX;
    }

    /**
     * @brief Check if the block is ready to perform work.
     */
    bool is_ready() override;

private:
    std::shared_ptr<SdrDevice> device_;
    DataType data_type_;

    // Lock-free buffer for samples from device
    std::unique_ptr<RingBuffer> rx_buffer_;

    // Sample size in bytes
    size_t sample_size_;

    /**
     * @brief Rx callback for the SDR device
     * This is called by the device when it has samples available.
     * @param data Pointer to received data
     * @param length Length of data in bytes
     */
    void rxCallback(const uint8_t* data, size_t length);

    // Pre-allocated scratch buffers for zero-allocation work()
    std::vector<int8_t> raw_chunk_;
    std::vector<std::complex<float>> float_chunk_;
};