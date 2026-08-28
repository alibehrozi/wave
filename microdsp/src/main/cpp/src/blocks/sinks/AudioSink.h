#ifndef AUDIO_SINK_H
#define AUDIO_SINK_H

#include "core/Block.h"
#include <vector>
#include <atomic>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <algorithm>  // For std::clamp

// Android Audio Includes
#ifdef __ANDROID__
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#endif

/**
 * @class AudioSink
 * @brief Audio output block that plays audio to the system's audio hardware.
 * 
 * This block receives audio data (float or short) and outputs it through
 * the system's audio output. On Android, it uses OpenSL ES for low-latency
 * audio playback.
 * 
 * @section Usage Example
 * @code
 * // Create audio sink
 * auto audio_sink = AudioSink::make(DataType::FLOAT, 44100, 2, "speaker");
 * audio_sink->set_volume(0.8f);
 * audio_sink->start_playback();
 * @endcode
 */
class AudioSink : public Block {
public:
    using sptr = std::shared_ptr<AudioSink>;

    struct AudioConfig {
        int sample_rate;
        int channels;
        int buffer_size;
        int bits_per_sample;
    };

    /**
     * @brief Create an AudioSink
     * @param data_type Audio data type (FLOAT or SHORT only)
     * @param sample_rate Sample rate in Hz
     * @param channels Number of audio channels (1=mono, 2=stereo)
     * @param name Block name
     */
    AudioSink(DataType data_type, int sample_rate = 44100, int channels = 1,
              const std::string& name = "audio_sink")
            : Block(name),
              data_type_(data_type),
              sample_rate_(sample_rate),
              channels_(channels),
              audio_initialized_(false),
              playing_(false),
              volume_(1.0f),
              buffer_size_(1024) {

        // Add input port
        add_input_port("in", port_config::fixed_type(data_type));

        // Set parameters
        set_parameter("sample_rate", sample_rate);
        set_parameter("channels", channels);
        set_parameter("buffer_size", 1024);
        set_parameter("volume", 1.0f);

        // Validate data type for audio
        if (data_type != DataType::FLOAT && data_type != DataType::SHORT) {
            LOGE("AudioSink: Only FLOAT and SHORT data types are supported for audio");
        }
    }

    /**
     * @brief Factory method
     */
    static sptr make(DataType data_type, int sample_rate = 44100, int channels = 1,
                     const std::string& name = "audio_sink") {
        return std::make_shared<AudioSink>(data_type, sample_rate, channels, name);
    }

    ~AudioSink() override {
        stop_playback();
        cleanup_audio();
    }

    /**
     * @brief Process incoming audio data
     * 
     * This method is called by the pipeline/scheduler to process audio data.
     * It reads from the input port and fills audio buffers.
     */
    void work() override;

    /**
     * @brief Check if block is ready to perform work
     */
    bool is_ready() override;

    /**
     * @brief Create native block for JNI
     * @param name Block name
     * @return Native handle
     */
    int64_t nativeCreateBlock(const std::string& name) override {
        // This is called from JNI to create the native object
        // The actual creation happens in the Java JNI wrapper
        return reinterpret_cast<long>(this);
    }

    /**
     * @brief Initialize audio hardware
     * @return true if successful
     */
    bool initialize_audio();

    /**
     * @brief Clean up audio hardware
     */
    void cleanup_audio();

    /**
     * @brief Start audio playback
     * @return true if successful
     */
    bool start_playback();

    /**
     * @brief Stop audio playback
     */
    void stop_playback();

    /**
     * @brief Pause audio playback
     */
    void pause_playback();

    /**
     * @brief Resume audio playback
     */
    void resume_playback();

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
     * @brief Set playback volume
     * @param volume Volume level (0.0 to 1.0)
     */
    void set_volume(float volume);

    /**
     * @brief Set audio buffer size
     * @param buffer_size Buffer size in samples
     */
    void set_buffer_size(int buffer_size);

    /**
     * @brief Check if audio is playing
     * @return true if playing
     */
    bool is_playing() const {
        return playing_.load(std::memory_order_acquire);
    }

    /**
     * @brief Check if audio is initialized
     * @return true if initialized
     */
    bool is_initialized() const {
        return audio_initialized_.load(std::memory_order_acquire);
    }

    /**
     * @brief Get audio latency in milliseconds
     * @return Latency in ms
     */
    int get_latency() const;

    /**
     * @brief Get number of samples played
     * @return Sample count
     */
    size_t get_samples_played() const {
        return samples_played_.load(std::memory_order_acquire);
    }

private:

    DataType data_type_;
    int sample_rate_;
    int channels_;
    std::atomic<bool> audio_initialized_;
    std::atomic<bool> playing_;
    std::atomic<size_t> samples_played_{0};

    float volume_;
    int buffer_size_;

    // Audio buffers & SPSC FIFO
    std::unique_ptr<RingBuffer> audio_fifo_;
    std::vector<int16_t> playback_buffer_;
    std::vector<int16_t> conversion_buffer_;
    std::mutex buffer_mutex_;

#ifdef __ANDROID__
    // OpenSL ES objects
    SLObjectItf engine_object_ = nullptr;
    SLEngineItf engine_engine_ = nullptr;
    SLObjectItf output_mix_object_ = nullptr;
    SLObjectItf player_object_ = nullptr;
    SLPlayItf player_play_ = nullptr;
    SLAndroidSimpleBufferQueueItf player_buffer_queue_ = nullptr;

    // OpenSL ES callbacks
    static void sl_buffer_queue_callback(SLAndroidSimpleBufferQueueItf caller, void* context);
    void process_buffer_queue();

    bool create_opensl_engine();
    bool create_audio_player();
    void destroy_audio_player();
    void destroy_opensl_engine();
#endif

    /**
     * @brief Fill audio buffers with incoming data
     */
    void fill_audio_buffers();

    /**
     * @brief Convert audio data to 16-bit PCM
     * @param input Input data
     * @param sample_count Number of samples
     */
    void convert_audio_data(const void* input, size_t sample_count);

    /**
     * @brief Get audio buffer for playback
     * @param buffer Output buffer pointer
     * @return Buffer size in bytes
     */
    size_t get_audio_buffer(void** buffer);
};

#endif // AUDIO_SINK_H