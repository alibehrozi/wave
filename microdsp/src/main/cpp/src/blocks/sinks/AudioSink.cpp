#include "AudioSink.h"
#include <android/log.h>
#include <algorithm>
#include <iostream>

#define LOG_TAG "AudioSink"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#ifdef __ANDROID__

bool AudioSink::initialize_audio() {
    if (audio_initialized_.load(std::memory_order_acquire)) {
        LOGI("AudioSink: Already initialized");
        return true;
    }

    LOGI("AudioSink: Initializing audio...");
    LOGI("  - Sample rate: %d Hz", sample_rate_);
    LOGI("  - Channels: %d", channels_);
    LOGI("  - Data type: %d", static_cast<int>(data_type_));

    if (!create_opensl_engine()) {
        LOGE("AudioSink: Failed to create OpenSL ES engine");
        return false;
    }

    if (!create_audio_player()) {
        LOGE("AudioSink: Failed to create audio player");
        destroy_opensl_engine();
        return false;
    }

    // Initialize audio buffers and SPSC FIFO
    buffer_size_ = get_int_parameter("buffer_size", 1024);
    audio_fifo_ = std::make_unique<RingBuffer>(16384, DataType::SHORT);
    playback_buffer_.assign(buffer_size_ * channels_, 0);
    conversion_buffer_.resize(buffer_size_ * channels_);

    volume_ = static_cast<float>(get_double_parameter("volume", 1.0));

    audio_initialized_.store(true, std::memory_order_release);
    LOGI("AudioSink: Audio initialized successfully - %dHz, %d channels",
         sample_rate_, channels_);

    return true;
}

void AudioSink::cleanup_audio() {
    LOGI("AudioSink: Cleaning up audio");
    stop_playback();

    if (audio_initialized_.load(std::memory_order_acquire)) {
        destroy_audio_player();
        destroy_opensl_engine();
        audio_initialized_.store(false, std::memory_order_release);
    }
}

bool AudioSink::create_opensl_engine() {
    SLresult result;

    // Create engine
    result = slCreateEngine(&engine_object_, 0, NULL, 0, NULL, NULL);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to create engine: %d", result);
        return false;
    }

    // Realize the engine
    result = (*engine_object_)->Realize(engine_object_, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to realize engine: %d", result);
        return false;
    }

    // Get the engine interface
    result = (*engine_object_)->GetInterface(engine_object_, SL_IID_ENGINE, &engine_engine_);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to get engine interface: %d", result);
        return false;
    }

    // Create output mix
    result = (*engine_engine_)->CreateOutputMix(engine_engine_, &output_mix_object_, 0, NULL, NULL);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to create output mix: %d", result);
        return false;
    }

    // Realize the output mix
    result = (*output_mix_object_)->Realize(output_mix_object_, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to realize output mix: %d", result);
        return false;
    }

    LOGI("AudioSink: OpenSL ES engine created");
    return true;
}

bool AudioSink::create_audio_player() {
    SLresult result;

    // Configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
    };

    SLDataFormat_PCM format_pcm = {
            SL_DATAFORMAT_PCM,
            static_cast<SLuint32>(channels_),
            static_cast<SLuint32>(sample_rate_ * 1000), // in mHz
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            (channels_ == 2) ? (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT) : SL_SPEAKER_FRONT_CENTER,
            SL_BYTEORDER_LITTLEENDIAN
    };

    SLDataSource audio_src = {&loc_bufq, &format_pcm};

    // Configure audio sink
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, output_mix_object_};
    SLDataSink audio_snk = {&loc_outmix, NULL};

    // Create audio player
    const SLInterfaceID ids[2] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME};
    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

    result = (*engine_engine_)->CreateAudioPlayer(engine_engine_, &player_object_,
                                                  &audio_src, &audio_snk,
                                                  2, ids, req);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to create audio player: %d", result);
        return false;
    }

    // Realize the player
    result = (*player_object_)->Realize(player_object_, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to realize audio player: %d", result);
        return false;
    }

    // Get the play interface
    result = (*player_object_)->GetInterface(player_object_, SL_IID_PLAY, &player_play_);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to get play interface: %d", result);
        return false;
    }

    // Get the buffer queue interface
    result = (*player_object_)->GetInterface(player_object_, SL_IID_BUFFERQUEUE, &player_buffer_queue_);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to get buffer queue interface: %d", result);
        return false;
    }

    // Register callback
    result = (*player_buffer_queue_)->RegisterCallback(player_buffer_queue_, sl_buffer_queue_callback, this);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("AudioSink: Failed to register callback: %d", result);
        return false;
    }

    LOGI("AudioSink: Audio player created");
    return true;
}

void AudioSink::destroy_audio_player() {
    if (player_object_ != nullptr) {
        (*player_object_)->Destroy(player_object_);
        player_object_ = nullptr;
        player_play_ = nullptr;
        player_buffer_queue_ = nullptr;
        LOGI("AudioSink: Audio player destroyed");
    }
}

void AudioSink::destroy_opensl_engine() {
    if (output_mix_object_ != nullptr) {
        (*output_mix_object_)->Destroy(output_mix_object_);
        output_mix_object_ = nullptr;
    }

    if (engine_object_ != nullptr) {
        (*engine_object_)->Destroy(engine_object_);
        engine_object_ = nullptr;
        engine_engine_ = nullptr;
    }
    LOGI("AudioSink: OpenSL ES engine destroyed");
}

void AudioSink::sl_buffer_queue_callback(SLAndroidSimpleBufferQueueItf caller, void* context) {
    AudioSink* sink = static_cast<AudioSink*>(context);
    if (sink) {
        sink->process_buffer_queue();
    }
}

void AudioSink::process_buffer_queue() {
    if (!playing_.load(std::memory_order_acquire)) {
        return;
    }

    void* buffer = nullptr;
    size_t buffer_size = get_audio_buffer(&buffer);

    if (buffer && buffer_size > 0) {
        SLresult result = (*player_buffer_queue_)->Enqueue(player_buffer_queue_, buffer, buffer_size);
        if (result == SL_RESULT_SUCCESS) {
            samples_played_.fetch_add(buffer_size / (channels_ * sizeof(int16_t)),
                                      std::memory_order_relaxed);
        }
    }
}

#else // !__ANDROID__

bool AudioSink::initialize_audio() {
    LOGI("AudioSink: Audio not supported on this platform");
    return false;
}

void AudioSink::cleanup_audio() {
    // Nothing to clean up
}

#endif // __ANDROID__

bool AudioSink::start_playback() {
    if (!audio_initialized_.load(std::memory_order_acquire)) {
        if (!initialize_audio()) {
            LOGE("AudioSink: Failed to initialize audio for playback");
            return false;
        }
    }

    playing_.store(true, std::memory_order_release);

#ifdef __ANDROID__
    // Enqueue initial buffers
    if (player_play_) {
        (*player_play_)->SetPlayState(player_play_, SL_PLAYSTATE_PLAYING);
    }
    for (int i = 0; i < 2; i++) {
        process_buffer_queue();
    }
#endif

    LOGI("AudioSink: Playback started");
    return true;
}

void AudioSink::stop_playback() {
    playing_.store(false, std::memory_order_release);

#ifdef __ANDROID__
    if (player_play_) {
        (*player_play_)->SetPlayState(player_play_, SL_PLAYSTATE_STOPPED);
    }

    if (player_buffer_queue_) {
        (*player_buffer_queue_)->Clear(player_buffer_queue_);
    }
#endif

    LOGI("AudioSink: Playback stopped");
}

void AudioSink::pause_playback() {
    playing_.store(false, std::memory_order_release);

#ifdef __ANDROID__
    if (player_play_) {
        (*player_play_)->SetPlayState(player_play_, SL_PLAYSTATE_PAUSED);
    }
#endif

    LOGI("AudioSink: Playback paused");
}

void AudioSink::resume_playback() {
    playing_.store(true, std::memory_order_release);

#ifdef __ANDROID__
    if (player_play_) {
        (*player_play_)->SetPlayState(player_play_, SL_PLAYSTATE_PLAYING);
    }
#endif

    LOGI("AudioSink: Playback resumed");
}

bool AudioSink::set_sample_rate(int sample_rate) {
    if (audio_initialized_.load(std::memory_order_acquire)) {
        LOGE("AudioSink: Cannot change sample rate after initialization");
        return false;
    }

    sample_rate_ = sample_rate;
    set_parameter("sample_rate", sample_rate);
    LOGI("AudioSink: Sample rate set to %d", sample_rate);
    return true;
}

bool AudioSink::set_channels(int channels) {
    if (audio_initialized_.load(std::memory_order_acquire)) {
        LOGE("AudioSink: Cannot change channels after initialization");
        return false;
    }

    channels_ = channels;
    set_parameter("channels", channels);
    LOGI("AudioSink: Channels set to %d", channels);
    return true;
}

void AudioSink::set_volume(float volume) {
    volume_ = std::clamp(volume, 0.0f, 1.0f);
    set_parameter("volume", static_cast<double>(volume_));

#ifdef __ANDROID__
    if (player_object_) {
        SLVolumeItf volume_interface;
        SLresult result = (*player_object_)->GetInterface(player_object_, SL_IID_VOLUME, &volume_interface);
        if (result == SL_RESULT_SUCCESS) {
            // Use SetVolume with a multiplier (0.0 to 1.0)
            SLmillibel max_volume;
            result = (*volume_interface)->GetMaxVolumeLevel(volume_interface, &max_volume);
            if (result == SL_RESULT_SUCCESS) {
                // Convert volume (0.0-1.0) to millibels
                // Volume range is typically -2400 to 0 millibels (0 to -24 dB)
                SLmillibel volume_level = static_cast<SLmillibel>((1.0f - volume_) * -2400.0f);
                (*volume_interface)->SetVolumeLevel(volume_interface, volume_level);
                LOGI("AudioSink: Volume set to %.2f (%d mB)", volume_, volume_level);
            }
        }
    }
#endif

    LOGI("AudioSink: Volume set to %.2f", volume_);
}

void AudioSink::set_buffer_size(int buffer_size) {
    if (audio_initialized_.load(std::memory_order_acquire)) {
        LOGE("AudioSink: Cannot change buffer size after initialization");
        return;
    }

    buffer_size_ = buffer_size;
    set_parameter("buffer_size", buffer_size);
    LOGI("AudioSink: Buffer size set to %d", buffer_size);
}

int AudioSink::get_latency() const {
    // Estimate latency based on buffer size
    if (sample_rate_ == 0) return 0;
    return (buffer_size_ * 1000) / sample_rate_;
}

void AudioSink::work() {
    if (!playing_.load(std::memory_order_acquire)) {
        return;
    }

    auto* in_port = get_input_port("in");
    if (!in_port) return;

    // Fill audio buffers with incoming data
    fill_audio_buffers();
}

bool AudioSink::is_ready() {
    auto* in_port = get_input_port("in");
    if (!in_port || !playing_.load(std::memory_order_acquire)) return false;

    if (data_type_ == DataType::FLOAT) {
        return in_port->items_available<float>() > 0;
    } else {
        return in_port->items_available<int16_t>() > 0;
    }
}

void AudioSink::fill_audio_buffers() {
    auto* in_port = get_input_port("in");
    if (!in_port || !audio_fifo_) return;

    // Read available data and push to audio FIFO
    size_t available = 0;
    if (data_type_ == DataType::FLOAT) {
        available = in_port->items_available<float>();
    } else if (data_type_ == DataType::SHORT) {
        available = in_port->items_available<int16_t>();
    }

    if (available > 0) {
        size_t fifo_space = audio_fifo_->write_available();
        size_t to_read = std::min({available, conversion_buffer_.size(), fifo_space});
        if (to_read == 0) return;

        if (data_type_ == DataType::FLOAT) {
            std::vector<float> temp_buffer(to_read);
            if (in_port->read(temp_buffer.data(), to_read)) {
                convert_audio_data(temp_buffer.data(), to_read);
                audio_fifo_->write(conversion_buffer_.data(), to_read);
            }
        } else if (data_type_ == DataType::SHORT) {
            if (in_port->read(conversion_buffer_.data(), to_read)) {
                audio_fifo_->write(conversion_buffer_.data(), to_read);
            }
        }
    }
}

void AudioSink::convert_audio_data(const void* input, size_t sample_count) {
    const size_t output_samples = sample_count;

    if (conversion_buffer_.size() < output_samples) {
        conversion_buffer_.resize(output_samples);
    }

    if (data_type_ == DataType::FLOAT) {
        const float* float_input = static_cast<const float*>(input);
        for (size_t i = 0; i < output_samples; ++i) {
            float sample = float_input[i] * volume_;
            sample = std::clamp(sample, -1.0f, 1.0f);
            conversion_buffer_[i] = static_cast<int16_t>(sample * 32767.0f);
        }
    } else if (data_type_ == DataType::SHORT) {
        const int16_t* short_input = static_cast<const int16_t*>(input);
        for (size_t i = 0; i < output_samples; ++i) {
            float sample = static_cast<float>(short_input[i]) / 32768.0f * volume_;
            sample = std::clamp(sample, -1.0f, 1.0f);
            conversion_buffer_[i] = static_cast<int16_t>(sample * 32767.0f);
        }
    }
}

size_t AudioSink::get_audio_buffer(void** buffer) {
    if (!audio_fifo_ || playback_buffer_.empty()) {
        *buffer = nullptr;
        return 0;
    }

    size_t needed = playback_buffer_.size();
    size_t available = audio_fifo_->read_available();

    if (available >= needed) {
        audio_fifo_->read(playback_buffer_.data(), needed);
    } else if (available > 0) {
        audio_fifo_->read(playback_buffer_.data(), available);
        // Fill remaining with silence
        std::fill(playback_buffer_.begin() + available, playback_buffer_.end(), static_cast<int16_t>(0));
    } else {
        // Full silence on underrun
        std::fill(playback_buffer_.begin(), playback_buffer_.end(), static_cast<int16_t>(0));
    }

    *buffer = playback_buffer_.data();
    return playback_buffer_.size() * sizeof(int16_t);
}