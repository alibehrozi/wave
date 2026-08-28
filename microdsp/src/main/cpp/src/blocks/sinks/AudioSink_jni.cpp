#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "microdsp.h"
#include "AudioSink.h"

#define LOG_TAG "AudioSink-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * @brief Throw a Java exception with the given message
 */
static void throwJavaException(JNIEnv* env, const char* className, const char* message) {
    jclass clazz = env->FindClass(className);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
    }
    env->DeleteLocalRef(clazz);
}

/**
 * @brief Get a shared_ptr from a handle with null check
 */
template<typename T>
static std::shared_ptr<T> getBlockFromHandle(JNIEnv* env, jlong handle, const char* context) {
    auto block = MicroDSP::get_instance().get_block<T>(handle);
    if (!block) {
        LOGE("%s - invalid handle: %lld", context, (long long)handle);
        throwJavaException(env, "java/lang/IllegalStateException",
                           (std::string(context) + " - invalid handle").c_str());
        return nullptr;
    }
    return block;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeCreateAudioSink(
        JNIEnv* env, jobject thiz, jint data_type, jint sample_rate, jint channels, jstring name) {

    if (name == nullptr) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Name cannot be null");
        return 0;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) {
        throwJavaException(env, "java/lang/OutOfMemoryError", "Failed to get string chars");
        return 0;
    }

    try {
        DataType cpp_data_type = static_cast<DataType>(data_type);

        // Validate data type
        if (cpp_data_type != DataType::FLOAT && cpp_data_type != DataType::SHORT) {
            env->ReleaseStringUTFChars(name, c_name);
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "AudioSink only supports FLOAT and SHORT data types");
            return 0;
        }

        // Validate sample rate
        if (sample_rate <= 0 || sample_rate > 192000) {
            env->ReleaseStringUTFChars(name, c_name);
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Invalid sample rate");
            return 0;
        }

        // Validate channels
        if (channels <= 0 || channels > 8) {
            env->ReleaseStringUTFChars(name, c_name);
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Invalid channel count (must be 1-8)");
            return 0;
        }

        auto audio_sink = std::make_shared<AudioSink>(cpp_data_type, sample_rate, channels, c_name);
        jlong handle = MicroDSP::get_instance().register_block(audio_sink);

        env->ReleaseStringUTFChars(name, c_name);
        LOGI("Created AudioSink: %s, handle: %lld, sample_rate: %d, channels: %d",
             c_name, (long long)handle, sample_rate, channels);
        return handle;

    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        LOGE("Exception creating AudioSink: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeDestroyAudioSink(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        audio_sink->stop();
        audio_sink->cleanup_audio();
        MicroDSP::get_instance().unregister_object(handle);
        LOGI("Destroyed AudioSink handle: %lld", (long long)handle);
    } else {
        LOGE("Failed to destroy AudioSink - invalid handle: %lld", (long long)handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeInitializeAudio(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = getBlockFromHandle<AudioSink>(env, handle, "initialize_audio");
    if (!audio_sink) return JNI_FALSE;

    try {
        bool result = audio_sink->initialize_audio();
        LOGI("AudioSink initialize_audio: %s for handle: %ld", result ? "SUCCESS" : "FAILED", handle);
        return result ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("AudioSink initialize_audio exception: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeCleanupAudio(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        audio_sink->cleanup_audio();
        LOGI("AudioSink cleanup_audio for handle: %ld", handle);
    } else {
        LOGE("AudioSink cleanup_audio failed - invalid handle: %ld", handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeStartPlayback(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = getBlockFromHandle<AudioSink>(env, handle, "start_playback");
    if (!audio_sink) return JNI_FALSE;

    try {
        bool result = audio_sink->start_playback();
        LOGI("AudioSink start_playback: %s for handle: %ld", result ? "SUCCESS" : "FAILED", handle);
        return result ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("AudioSink start_playback exception: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeStopPlayback(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        audio_sink->stop_playback();
        LOGI("AudioSink stop_playback for handle: %ld", handle);
    } else {
        LOGE("AudioSink stop_playback failed - invalid handle: %ld", handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativePausePlayback(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        audio_sink->pause_playback();
        LOGI("AudioSink pause_playback for handle: %ld", handle);
    } else {
        LOGE("AudioSink pause_playback failed - invalid handle: %ld", handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeResumePlayback(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        audio_sink->resume_playback();
        LOGI("AudioSink resume_playback for handle: %ld", handle);
    } else {
        LOGE("AudioSink resume_playback failed - invalid handle: %ld", handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeSetSampleRate(
        JNIEnv* env, jobject thiz, jlong handle, jint sample_rate) {

    auto audio_sink = getBlockFromHandle<AudioSink>(env, handle, "set_sample_rate");
    if (!audio_sink) return JNI_FALSE;

    if (sample_rate <= 0 || sample_rate > 192000) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Invalid sample rate");
        return JNI_FALSE;
    }

    bool result = audio_sink->set_sample_rate(sample_rate);
    LOGI("AudioSink set_sample_rate: %s for handle: %ld", result ? "SUCCESS" : "FAILED", handle);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeSetChannels(
        JNIEnv* env, jobject thiz, jlong handle, jint channels) {

    auto audio_sink = getBlockFromHandle<AudioSink>(env, handle, "set_channels");
    if (!audio_sink) return JNI_FALSE;

    if (channels <= 0 || channels > 8) {
        throwJavaException(env, "java/lang/IllegalArgumentException",
                           "Invalid channel count (must be 1-8)");
        return JNI_FALSE;
    }

    bool result = audio_sink->set_channels(channels);
    LOGI("AudioSink set_channels: %s for handle: %ld", result ? "SUCCESS" : "FAILED", handle);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeSetVolume(
        JNIEnv* env, jobject thiz, jlong handle, jfloat volume) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        if (volume < 0.0f || volume > 1.0f) {
            LOGE("AudioSink set_volume: volume out of range: %.2f", volume);
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Volume must be between 0.0 and 1.0");
            return;
        }
        audio_sink->set_volume(volume);
        LOGI("AudioSink set_volume: %.2f for handle: %ld", volume, handle);
    } else {
        LOGE("AudioSink set_volume failed - invalid handle: %ld", handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeSetBufferSize(
        JNIEnv* env, jobject thiz, jlong handle, jint buffer_size) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        if (buffer_size <= 0) {
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Buffer size must be positive");
            return;
        }
        audio_sink->set_buffer_size(buffer_size);
        LOGI("AudioSink set_buffer_size: %d for handle: %ld", buffer_size, handle);
    } else {
        LOGE("AudioSink set_buffer_size failed - invalid handle: %ld", handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeIsPlaying(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        bool is_playing = audio_sink->is_playing();
        LOGI("AudioSink is_playing: %s for handle: %ld", is_playing ? "true" : "false", handle);
        return is_playing ? JNI_TRUE : JNI_FALSE;
    } else {
        LOGE("AudioSink is_playing failed - invalid handle: %ld", handle);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeIsInitialized(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        bool is_initialized = audio_sink->is_initialized();
        LOGI("AudioSink is_initialized: %s for handle: %ld", is_initialized ? "true" : "false", handle);
        return is_initialized ? JNI_TRUE : JNI_FALSE;
    } else {
        LOGE("AudioSink is_initialized failed - invalid handle: %ld", handle);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeGetLatency(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        int latency = audio_sink->get_latency();
        LOGI("AudioSink get_latency: %d ms for handle: %ld", latency, handle);
        return latency;
    } else {
        LOGE("AudioSink get_latency failed - invalid handle: %ld", handle);
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_AudioSink_nativeGetSamplesPlayed(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto audio_sink = MicroDSP::get_instance().get_block<AudioSink>(handle);
    if (audio_sink) {
        long samples_played = audio_sink->get_samples_played();
        LOGI("AudioSink get_samples_played: %ld for handle: %ld", samples_played, handle);
        return samples_played;
    } else {
        LOGE("AudioSink get_samples_played failed - invalid handle: %ld", handle);
        return 0;
    }
}