#include "SdrSource.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "SdrSource"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#endif

// Convert interleaved int8_t [I0, Q0, I1, Q1, ...] to std::complex<float>
static void convert_int8_iq_to_complex_float(const int8_t* in, std::complex<float>* out, size_t count) {
    size_t i = 0;
    constexpr float scale = 1.0f / 128.0f;

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    float32x4_t vscale = vdupq_n_f32(scale);
    for (; i + 7 < count; i += 8) {
        int8x8x2_t iq = vld2_s8(in + 2 * i);

        int16x4_t i_s16_low = vget_low_s16(vmovl_s8(iq.val[0]));
        int16x4_t q_s16_low = vget_low_s16(vmovl_s8(iq.val[1]));
        int32x4_t i_s32_low = vmovl_s16(i_s16_low);
        int32x4_t q_s32_low = vmovl_s16(q_s16_low);

        float32x4_t i_f32_low = vmulq_f32(vcvtq_f32_s32(i_s32_low), vscale);
        float32x4_t q_f32_low = vmulq_f32(vcvtq_f32_s32(q_s32_low), vscale);

        float32x4x2_t out_low = { i_f32_low, q_f32_low };
        vst2q_f32(reinterpret_cast<float*>(out + i), out_low);

        int16x4_t i_s16_high = vget_high_s16(vmovl_s8(iq.val[0]));
        int16x4_t q_s16_high = vget_high_s16(vmovl_s8(iq.val[1]));
        int32x4_t i_s32_high = vmovl_s16(i_s16_high);
        int32x4_t q_s32_high = vmovl_s16(q_s16_high);

        float32x4_t i_f32_high = vmulq_f32(vcvtq_f32_s32(i_s32_high), vscale);
        float32x4_t q_f32_high = vmulq_f32(vcvtq_f32_s32(q_s32_high), vscale);

        float32x4x2_t out_high = { i_f32_high, q_f32_high };
        vst2q_f32(reinterpret_cast<float*>(out + i + 4), out_high);
    }
#endif

    for (; i < count; ++i) {
        float re = static_cast<float>(in[2 * i]) * scale;
        float im = static_cast<float>(in[2 * i + 1]) * scale;
        out[i] = std::complex<float>(re, im);
    }
}

constexpr size_t SDR_CHUNK_SIZE = 4096;

SdrSource::SdrSource(std::shared_ptr<SdrDevice> device,
                     const std::string& name)
        : Block(name),
          device_(device),
          data_type_(DataType::COMPLEX_FLOAT) {

    if (!device_) {
        throw std::runtime_error("SDR device cannot be null");
    }

    // 1 raw IQ sample from HackRF is 2 bytes (int8_t I, int8_t Q)
    sample_size_ = 2 * sizeof(int8_t);

    // Pre-allocate scratch buffers to prevent allocations during work()
    raw_chunk_.resize(SDR_CHUNK_SIZE * 2);
    float_chunk_.resize(SDR_CHUNK_SIZE);

    // Create internal ring buffer for raw data (256k raw bytes)
    rx_buffer_ = std::make_unique<RingBuffer>(262144, DataType::BYTE);

    // Add output port with the specified data type
    add_output_port("out", port_config::fixed_type(data_type_));

    LOGI("SdrSource created: %s, data type: %d, sample size: %zu bytes (int8 IQ)",
         name.c_str(), static_cast<int>(data_type_), sample_size_);
}

SdrSource::~SdrSource() {
    stop();
}

int SdrSource::start() {
    if (is_active()) {
        LOGI("SdrSource: Already started");
        return 0;
    }

    rx_buffer_->clear();

    // Register RX callback with the device
    device_->setRxCallback(
            [this](const uint8_t* data, size_t length) {
                this->rxCallback(data, length);
            }
    );

    int result = device_->startRx();

    if (result != 0) {
        LOGE("SdrSource: Failed to start RX");
        return -1;
    }

    return Block::start();
}

void SdrSource::stop() {
    if (!is_active()) {
        return;
    }

    // Stop the device streaming
    device_->stopRx();
    device_->setRxCallback(nullptr);

    Block::stop();
    LOGI("SdrSource: Stopped");
}

void SdrSource::rxCallback(const uint8_t* data, size_t length) {
    if (!rx_buffer_->write(data, length)) {
        LOGV("SdrSource: Internal buffer overflow, dropping %zu bytes", length);
    }
}

bool SdrSource::is_ready() {
    return is_active() && rx_buffer_->read_available() >= sample_size_;
}

void SdrSource::work() {
    if (!is_active()) {
        return;
    }

    auto* out_port = get_output_port("out");
    if (!out_port) {
        return;
    }

    // Check how much raw IQ byte pairs we have
    size_t available_bytes = rx_buffer_->read_available();
    if (available_bytes < sample_size_) {
        return;
    }

    size_t available_samples = available_bytes / sample_size_;
    size_t samples_to_process = std::min(available_samples, SDR_CHUNK_SIZE);
    size_t bytes_to_read = samples_to_process * sample_size_;

    if (rx_buffer_->read(reinterpret_cast<uint8_t*>(raw_chunk_.data()), bytes_to_read)) {
        convert_int8_iq_to_complex_float(raw_chunk_.data(), float_chunk_.data(), samples_to_process);
        if (!out_port->write(float_chunk_.data(), samples_to_process)) {
            LOGE("SdrSource: Failed to write to output port");
        }
    }
}
