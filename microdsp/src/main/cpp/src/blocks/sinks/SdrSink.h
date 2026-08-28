#pragma once

#include <memory>
#include <mutex>
#include <atomic>
#include <vector>
#include <condition_variable>
#include "core/Block.h"
#include "hardware/sdr.h"

/**
 * @class SdrSink
 * @brief SDR output block that transmits IQ samples through an SDR device.
 *
 * This block receives IQ samples (complex float or complex double) and
 * transmits them through the SDR hardware using the device's TxCallback.
 *
 * The SDR device should be fully configured (frequency, gain, sample rate)
 * before being passed to this block. Configuration is done directly through
 * the Sdr interface, not through this block.
 *
 * This block is passive - starting and stopping the flow of samples from
 * the processing pipeline to the device.
 */
class SdrSink : public Block {
public:
    using sptr = std::shared_ptr<SdrSink>;

    /**
     * @brief Create an SdrSink
     * @param device SDR device instance (must already be configured)
     * @param data_type IQ data type (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param name Block name
     */
    SdrSink(std::shared_ptr<SdrDevice> device, DataType data_type,
            const std::string &name = "sdr_sink");

    /**
     * @brief Factory method
     */
    static sptr make(const std::shared_ptr<SdrDevice>& device, DataType data_type,
                     const std::string &name = "sdr_sink") {
        return std::make_shared<SdrSink>(device, data_type, name);
    }

    ~SdrSink() override;

    /**
     * @brief Process incoming IQ data
     *
     * This method is called by the pipeline/scheduler to process data.
     * It reads from the input port and stores samples in the buffer.
     */
    void work() override;

    /**
     * @brief Create native block for JNI
     * @param name Block name
     * @return Native handle
     */
    int64_t nativeCreateBlock(const std::string &name) override {
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
     * @brief Checks if the device is currently transmitting samples.
     * @return true if transmitting, false otherwise
     */
    bool isTransmitting() const {
        auto state = device_->getStreamingState();
        return state == SdrStreamingState::TX || state == SdrStreamingState::RX_TX;
    }

    /**
     * @brief Check if the block is ready to perform work.
     */
    bool is_ready() override;

private:
    std::shared_ptr<SdrDevice> device_;
    DataType data_type_;

    // Lock-free buffer for samples
    std::unique_ptr<RingBuffer> tx_buffer_;

    // Sample size in bytes
    size_t sample_size_;

    /**
     * @brief Tx callback for the SDR device
     * This is called by the device when it needs samples to transmit.
     * @param buffer Pointer to fill with data
     * @param length Length of buffer in bytes
     * @return true if data was provided, false otherwise
     */
    bool txCallback(uint8_t *buffer, size_t length);

    // Pre-allocated scratch buffers for zero-allocation work()
    std::vector<std::complex<float>> float_chunk_;
    std::vector<int8_t> raw_chunk_;
};