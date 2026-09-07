#include "SdrSink.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>
#include <utility>

#define LOG_TAG "SdrSink"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#endif

// Convert std::complex<float> to interleaved int8_t [I0, Q0, I1, Q1, ...]
static void convert_complex_float_to_int8_iq(const std::complex<float>* in, int8_t* out, size_t count) {
    constexpr float scale = 127.0f;
    for (size_t i = 0; i < count; ++i) {
        float re = in[i].real() * scale;
        float im = in[i].imag() * scale;
        int i_val = std::clamp(static_cast<int>(std::round(re)), -128, 127);
        int q_val = std::clamp(static_cast<int>(std::round(im)), -128, 127);
        out[2 * i] = static_cast<int8_t>(i_val);
        out[2 * i + 1] = static_cast<int8_t>(q_val);
    }
}

constexpr size_t SDR_SINK_CHUNK_SIZE = 4096;

SdrSink::SdrSink(std::shared_ptr<SdrDevice> device, DataType data_type,
                 const std::string& name)
        : Block(name),
          device_(std::move(device)) ,
          data_type_(data_type) {

    if (!device_) {
        throw std::runtime_error("SDR device cannot be null");
    }

    // 1 raw IQ sample sent to HackRF is 2 bytes (int8_t I, int8_t Q)
    sample_size_ = 2 * sizeof(int8_t);

    // Pre-allocate scratch buffers
    float_chunk_.resize(SDR_SINK_CHUNK_SIZE);
    raw_chunk_.resize(SDR_SINK_CHUNK_SIZE * 2);

    // Create internal ring buffer for raw data (2 MB to easily hold multiple 256k HackRF USB transfers)
    tx_buffer_ = std::make_unique<RingBuffer>(2097152, DataType::BYTE);

    // Add input port with the specified data type
    add_input_port("in", port_config::fixed_type(data_type_));

    LOGI("SdrSink created: %s, data type: %d, sample size: %zu bytes (int8 IQ), buffer: 2MB",
         name.c_str(), static_cast<int>(data_type_), sample_size_);
}

SdrSink::~SdrSink() {
    stop();
}

int SdrSink::start() {
    if (is_active()) {
        LOGI("SdrSink: Already started");
        return 0;
    }

    tx_buffer_->clear();

    // Register TX callback with the device
    device_->setTxCallback(
            [this](uint8_t* buffer, size_t length) -> bool {
                return this->txCallback(buffer, length);
            }
    );

    int result = device_->startTx();

    if (result != 0) {
        LOGE("SdrSink: Failed to start TX");
        return -1;
    }

    return Block::start();
}

void SdrSink::stop() {
    if (!is_active()) {
        return;
    }

    // Stop the device streaming
    device_->stopTx();
    device_->setTxCallback(nullptr);

    Block::stop();
    LOGI("SdrSink: Stopped");
}

bool SdrSink::txCallback(uint8_t* buffer, size_t length) {
    size_t available = tx_buffer_->read_available();
    if (available >= length) {
        return tx_buffer_->read(buffer, length);
    }

    if (available > 0) {
        tx_buffer_->read(buffer, available);
        // Pad remainder with zeros
        std::memset(buffer + available, 0, length - available);
        return true;
    }

    // Underrun: no samples ready yet, fill with zero carrier
    std::memset(buffer, 0, length);
    return false;
}

bool SdrSink::is_ready() {
    auto* in_port = get_input_port("in");
    return is_active() && in_port && in_port->items_available<std::complex<float>>() > 0 &&
           tx_buffer_->write_available() >= SDR_SINK_CHUNK_SIZE * sample_size_;
}

void SdrSink::work() {
    if (!is_active()) {
        return;
    }

    auto* in_port = get_input_port("in");
    if (!in_port) {
        return;
    }

    // Read available items from input port
    size_t available = in_port->items_available<std::complex<float>>();
    if (available == 0) {
        return;
    }

    size_t write_space_samples = tx_buffer_->write_available() / sample_size_;
    if (write_space_samples == 0) {
        return;
    }

    size_t samples_to_process = std::min({available, write_space_samples, SDR_SINK_CHUNK_SIZE});

    if (in_port->read(float_chunk_.data(), samples_to_process)) {
        convert_complex_float_to_int8_iq(float_chunk_.data(), raw_chunk_.data(), samples_to_process);
        size_t bytes_to_write = samples_to_process * sample_size_;
        tx_buffer_->write(reinterpret_cast<const uint8_t*>(raw_chunk_.data()), bytes_to_write);
    }
}
