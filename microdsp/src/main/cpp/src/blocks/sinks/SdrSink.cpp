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
    size_t i = 0;
    constexpr float scale = 127.0f;

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    float32x4_t vscale = vdupq_n_f32(scale);
    for (; i + 3 < count; i += 4) {
        float32x4x2_t iq = vld2q_f32(reinterpret_cast<const float*>(in + i));

        int32x4_t i_s32 = vcvtq_s32_f32(vmulq_f32(iq.val[0], vscale));
        int32x4_t q_s32 = vcvtq_s32_f32(vmulq_f32(iq.val[1], vscale));

        int16x4_t i_s16 = vqmovn_s32(i_s32);
        int16x4_t q_s16 = vqmovn_s32(q_s32);

        int8x8_t i_s8 = vqmovn_s16(vcombine_s16(i_s16, i_s16));
        int8x8_t q_s8 = vqmovn_s16(vcombine_s16(q_s16, q_s16));

        int8x8x2_t out_iq;
        out_iq.val[0] = i_s8;
        out_iq.val[1] = q_s8;

        vst2_lane_s8(out + 2 * i, out_iq, 0);
        vst2_lane_s8(out + 2 * i + 2, out_iq, 1);
        vst2_lane_s8(out + 2 * i + 4, out_iq, 2);
        vst2_lane_s8(out + 2 * i + 6, out_iq, 3);
    }
#endif

    for (; i < count; ++i) {
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

    // Create internal ring buffer for raw data (fixed size: 256k raw bytes)
    tx_buffer_ = std::make_unique<RingBuffer>(262144, DataType::BYTE);

    // Add input port with the specified data type
    add_input_port("in", port_config::fixed_type(data_type_));

    LOGI("SdrSink created: %s, data type: %d, sample size: %zu bytes (int8 IQ)",
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
    if (tx_buffer_->read_available() < length) {
        // Not enough data - fill with zeros (silence/zeros)
        std::memset(buffer, 0, length);
        return false;
    }

    return tx_buffer_->read(buffer, length);
}

bool SdrSink::is_ready() {
    auto* in_port = get_input_port("in");
    return is_active() && in_port && in_port->items_available<std::complex<float>>() > 0;
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

    size_t samples_to_process = std::min(available, SDR_SINK_CHUNK_SIZE);

    if (in_port->read(float_chunk_.data(), samples_to_process)) {
        convert_complex_float_to_int8_iq(float_chunk_.data(), raw_chunk_.data(), samples_to_process);
        size_t bytes_to_write = samples_to_process * sample_size_;
        if (!tx_buffer_->write(reinterpret_cast<const uint8_t*>(raw_chunk_.data()), bytes_to_write)) {
            LOGV("SdrSink: Internal buffer overflow, dropping samples");
        }
    }
}
