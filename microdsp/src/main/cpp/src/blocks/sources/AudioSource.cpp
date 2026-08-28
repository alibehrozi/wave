#include "AudioSource.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>

#define LOG_TAG "AudioSource"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

AudioSource::AudioSource(DataType data_type, int sample_rate, int channels,
                         const std::string& name)
        : Block(name),
          data_type_(data_type),
          sample_rate_(sample_rate),
          channels_(channels),
          audio_initialized_(false),
          recording_(false) {

    // Validate data type
    if (data_type != DataType::FLOAT && data_type != DataType::SHORT) {
        LOGE("AudioSource: Only FLOAT and SHORT data types are supported");
        data_type_ = DataType::SHORT;
    }

    sample_size_ = (data_type_ == DataType::FLOAT) ? sizeof(float) : sizeof(int16_t);
    sample_size_ *= channels_;

    // Create internal lock-free ring buffer (e.g., 64k samples capacity)
    capture_buffer_ = std::make_unique<RingBuffer>(65536, DataType::BYTE);

    // Add output port
    add_output_port("out", port_config::fixed_type(data_type_));

    set_parameter("sample_rate", sample_rate);
    set_parameter("channels", channels);

    LOGI("AudioSource created: %s (%dHz, %d channels)", name.c_str(), sample_rate, channels);
}

AudioSource::~AudioSource() {
    stop();
    cleanup_audio();
}

int AudioSource::start() {
    if (recording_.load(std::memory_order_acquire)) {
        return 0;
    }

    if (!audio_initialized_.load(std::memory_order_acquire)) {
        if (!initialize_audio()) {
            return -1;
        }
    }

    capture_buffer_->clear();
    samples_captured_.store(0, std::memory_order_release);

#ifdef __ANDROID__
    if (recorder_record_) {
        SLresult result = (*recorder_record_)->SetRecordState(recorder_record_, SL_RECORDSTATE_RECORDING);
        if (result != SL_RESULT_SUCCESS) {
            LOGE("AudioSource: Failed to start recording: %d", result);
            return -1;
        }

        // Enqueue first buffer
        (*recorder_buffer_queue_)->Enqueue(recorder_buffer_queue_, hardware_buffer_.data(), hardware_buffer_.size() * sizeof(int16_t));
    }
#endif

    recording_.store(true, std::memory_order_release);
    Block::start();
    LOGI("AudioSource: Recording started");
    return 0;
}

void AudioSource::stop() {
    if (!recording_.exchange(false, std::memory_order_acq_rel)) {
        return;
    }

#ifdef __ANDROID__
    if (recorder_record_) {
        (*recorder_record_)->SetRecordState(recorder_record_, SL_RECORDSTATE_STOPPED);
    }
    if (recorder_buffer_queue_) {
        (*recorder_buffer_queue_)->Clear(recorder_buffer_queue_);
    }
#endif

    Block::stop();
    LOGI("AudioSource: Recording stopped");
}

bool AudioSource::is_ready() {
    return is_active() && capture_buffer_->read_available() >= sample_size_;
}

void AudioSource::work() {
    if (!recording_.load(std::memory_order_acquire)) {
        return;
    }

    auto* out_port = get_output_port("out");
    if (!out_port) return;

    size_t available_bytes = capture_buffer_->read_available();
    if (available_bytes < sample_size_) return;

    size_t available_samples = available_bytes / sample_size_;

    // Process in chunks (e.g., 512 samples)
    constexpr size_t CHUNK_SIZE = 512;
    size_t to_process = std::min(available_samples, CHUNK_SIZE);

    if (data_type_ == DataType::FLOAT) {
        std::vector<float> buffer(to_process * channels_);
        if (capture_buffer_->read(reinterpret_cast<uint8_t*>(buffer.data()), to_process * sample_size_)) {
            out_port->write(buffer.data(), to_process);
        }
    } else {
        std::vector<int16_t> buffer(to_process * channels_);
        if (capture_buffer_->read(reinterpret_cast<uint8_t*>(buffer.data()), to_process * sample_size_)) {
            out_port->write(buffer.data(), to_process);
        }
    }
}

bool AudioSource::initialize_audio() {
#ifdef __ANDROID__
    if (audio_initialized_.load(std::memory_order_acquire)) return true;

    if (!create_opensl_engine()) return false;
    if (!create_audio_recorder()) {
        destroy_opensl_engine();
        return false;
    }

    hardware_buffer_.resize(HW_BUFFER_SIZE * channels_);
    audio_initialized_.store(true, std::memory_order_release);
    return true;
#else
    return false;
#endif
}

void AudioSource::cleanup_audio() {
#ifdef __ANDROID__
    if (audio_initialized_.load(std::memory_order_acquire)) {
        destroy_audio_recorder();
        destroy_opensl_engine();
        audio_initialized_.store(false, std::memory_order_release);
    }
#endif
}

#ifdef __ANDROID__
bool AudioSource::create_opensl_engine() {
    SLresult result = slCreateEngine(&engine_object_, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*engine_object_)->Realize(engine_object_, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*engine_object_)->GetInterface(engine_object_, SL_IID_ENGINE, &engine_engine_);
    return result == SL_RESULT_SUCCESS;
}

bool AudioSource::create_audio_recorder() {
    SLresult result;

    // Source: Microphone
    SLDataLocator_IODevice loc_dev = {SL_DATALOCATOR_IODEVICE, SL_IODEVICE_AUDIOINPUT, SL_DEFAULTDEVICEID_AUDIOINPUT, NULL};
    SLDataSource audio_src = {&loc_dev, NULL};

    // Sink: Buffer Queue
    SLDataLocator_AndroidSimpleBufferQueue loc_bq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
    SLDataFormat_PCM format_pcm = {
            SL_DATAFORMAT_PCM,
            static_cast<SLuint32>(channels_),
            static_cast<SLuint32>(sample_rate_ * 1000),
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            (channels_ == 2) ? (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT) : SL_SPEAKER_FRONT_CENTER,
            SL_BYTEORDER_LITTLEENDIAN
    };
    SLDataSink audio_snk = {&loc_bq, &format_pcm};

    const SLInterfaceID ids[1] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
    const SLboolean req[1] = {SL_BOOLEAN_TRUE};

    result = (*engine_engine_)->CreateAudioRecorder(engine_engine_, &recorder_object_, &audio_src, &audio_snk, 1, ids, req);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*recorder_object_)->Realize(recorder_object_, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*recorder_object_)->GetInterface(recorder_object_, SL_IID_RECORD, &recorder_record_);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*recorder_object_)->GetInterface(recorder_object_, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &recorder_buffer_queue_);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*recorder_buffer_queue_)->RegisterCallback(recorder_buffer_queue_, sl_buffer_queue_callback, this);
    return result == SL_RESULT_SUCCESS;
}

void AudioSource::destroy_audio_recorder() {
    if (recorder_object_) {
        (*recorder_object_)->Destroy(recorder_object_);
        recorder_object_ = nullptr;
        recorder_record_ = nullptr;
        recorder_buffer_queue_ = nullptr;
    }
}

void AudioSource::destroy_opensl_engine() {
    if (engine_object_) {
        (*engine_object_)->Destroy(engine_object_);
        engine_object_ = nullptr;
        engine_engine_ = nullptr;
    }
}

void AudioSource::sl_buffer_queue_callback(SLAndroidSimpleBufferQueueItf caller, void* context) {
    auto* source = static_cast<AudioSource*>(context);
    if (source) {
        source->process_buffer_queue();
    }
}

void AudioSource::process_buffer_queue() {
    if (!recording_.load(std::memory_order_acquire)) return;

    // Data is in hardware_buffer_. Move it to our internal lock-free ring buffer.
    size_t bytes = hardware_buffer_.size() * sizeof(int16_t);

    if (data_type_ == DataType::SHORT) {
        capture_buffer_->write(reinterpret_cast<const uint8_t*>(hardware_buffer_.data()), bytes);
    } else {
        // Convert to float
        std::vector<float> float_data(hardware_buffer_.size());
        for (size_t i = 0; i < hardware_buffer_.size(); ++i) {
            float_data[i] = static_cast<float>(hardware_buffer_[i]) / 32768.0f;
        }
        capture_buffer_->write(reinterpret_cast<const uint8_t*>(float_data.data()), float_data.size() * sizeof(float));
    }

    samples_captured_.fetch_add(hardware_buffer_.size() / channels_, std::memory_order_relaxed);

    // Re-enqueue buffer
    (*recorder_buffer_queue_)->Enqueue(recorder_buffer_queue_, hardware_buffer_.data(), bytes);
}
#endif
