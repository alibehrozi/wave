#ifndef AUDIO_SOURCE_H
#define AUDIO_SOURCE_H

#include "core/Block.h"
#include <vector>
#include <atomic>
#include <mutex>
#include <condition_variable>

// Android Audio Includes
#ifdef __ANDROID__
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#endif

/**
 * @class AudioSource
 * @brief Audio input block that captures audio from the system's microphone.
 *
 * This block captures audio data (float or short) from the system's microphone
 * and outputs it to the DSP pipeline. On Android, it uses OpenSL ES for
 * low-latency audio capture.
 */
class AudioSource : public Block {
public:
    using sptr = std::shared_ptr<AudioSource>;

    /**
     * @brief Create an AudioSource
     * @param data_type Audio data type (FLOAT or SHORT only)
     * @param sample_rate Sample rate in Hz
     * @param channels Number of audio channels (1=mono, 2=stereo)
     * @param name Block name
     */
    AudioSource(DataType data_type, int sample_rate = 44100, int channels = 1,
                const std::string& name = "audio_source");

    /**
     * @brief Factory method
     */
    static sptr make(DataType data_type, int sample_rate = 44100, int channels = 1,
                     const std::string& name = "audio_source") {
        return std::make_shared<AudioSource>(data_type, sample_rate, channels, name);
    }

    ~AudioSource() override;

    /**
     * @brief Process captured audio data
     */
    void work() override;

    /**
     * @brief Check if block is ready to perform work
     */
    bool is_ready() override;

    /**
     * @brief Start audio capture
     * @return 0 on success, negative on error
     */
    int start() override;

    /**
     * @brief Stop audio capture
     */
    void stop() override;

    /**
     * @brief Set sample rate
     * @param sample_rate Sample rate in Hz
     * @return true if successful
     */
    bool set_sample_rate(int sample_rate);

    /**
     * @brief Set number of channels
     * @param channels Number of channels (1=mono, 2=stereo)
     * @return true if successful
     */
    bool set_channels(int channels);

    /**
     * @brief Check if audio is recording
     * @return true if recording
     */
    bool is_recording() const {
        return recording_.load(std::memory_order_acquire);
    }

    /**
     * @brief Get number of samples captured
     * @return Sample count
     */
    size_t get_samples_captured() const {
        return samples_captured_.load(std::memory_order_acquire);
    }

private:
    DataType data_type_;
    int sample_rate_;
    int channels_;
    std::atomic<bool> audio_initialized_;
    std::atomic<bool> recording_;
    std::atomic<size_t> samples_captured_{0};

    // Internal lock-free buffer for samples from hardware
    std::unique_ptr<RingBuffer> capture_buffer_;
    size_t sample_size_;

#ifdef __ANDROID__
    // OpenSL ES objects
    SLObjectItf engine_object_ = nullptr;
    SLEngineItf engine_engine_ = nullptr;
    SLObjectItf recorder_object_ = nullptr;
    SLRecordItf recorder_record_ = nullptr;
    SLAndroidSimpleBufferQueueItf recorder_buffer_queue_ = nullptr;

    // Buffer for OpenSL ES
    std::vector<int16_t> hardware_buffer_;
    static constexpr size_t HW_BUFFER_SIZE = 1024;

    // OpenSL ES callbacks
    static void sl_buffer_queue_callback(SLAndroidSimpleBufferQueueItf caller, void* context);
    void process_buffer_queue();

    bool create_opensl_engine();
    bool create_audio_recorder();
    void destroy_audio_recorder();
    void destroy_opensl_engine();
#endif

    /**
     * @brief Initialize audio hardware
     * @return true if successful
     */
    bool initialize_audio();

    /**
     * @brief Clean up audio hardware
     */
    void cleanup_audio();
};

#endif // AUDIO_SOURCE_H
