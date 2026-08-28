#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "microdsp.h"
#include "AudioSource.h"

#define LOG_TAG "AudioSource-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_AudioSource_nativeCreateAudioSource(
        JNIEnv* env, jclass thiz, jint data_type, jint sample_rate, jint channels, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) return 0;

    try {
        auto audio_source = std::make_shared<AudioSource>(
                static_cast<DataType>(data_type),
                sample_rate,
                channels,
                c_name);

        jlong handle = MicroDSP::get_instance().register_block(audio_source);

        env->ReleaseStringUTFChars(name, c_name);
        LOGI("Created AudioSource: %s, handle: %lld", c_name, (long long)handle);
        return handle;

    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        LOGE("Exception creating AudioSource: %s", e.what());
        jclass clazz = env->FindClass("java/lang/RuntimeException");
        if (clazz) env->ThrowNew(clazz, e.what());
        return 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_AudioSource_nativeStart(
        JNIEnv* env, jclass thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block<AudioSource>(handle);
    if (block) {
        return block->start() == 0 ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_AudioSource_nativeStop(
        JNIEnv* env, jclass thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block<AudioSource>(handle);
    if (block) {
        block->stop();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_AudioSource_nativeIsRecording(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block<AudioSource>(handle);
    if (block) {
        return block->is_recording() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}
