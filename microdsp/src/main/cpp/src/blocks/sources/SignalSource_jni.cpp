#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "../../microdsp.h"
#include "SignalSource.h"

#define LOG_TAG "SignalSource-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void throwJavaException(JNIEnv* env, const char* className, const char* message) {
    jclass clazz = env->FindClass(className);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
    }
    env->DeleteLocalRef(clazz);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_SignalSource_nativeCreateSignalSource(
        JNIEnv* env, jclass thiz, jint type, jdouble sample_rate, jdouble frequency, jdouble amplitude, jint signal_type, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) {
        return 0;
    }

    try {
        auto signal_source = std::make_shared<SignalSource>(
                static_cast<DataType>(type),
                sample_rate,
                frequency,
                amplitude,
                static_cast<SignalType>(signal_type),
                c_name);

        jlong handle = MicroDSP::get_instance().register_block(signal_source);
        env->ReleaseStringUTFChars(name, c_name);
        return handle;
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_SignalSource_nativeSetFrequency(
        JNIEnv* env, jobject thiz, jlong handle, jdouble frequency) {
    auto block = MicroDSP::get_instance().get_block<SignalSource>(handle);
    if (block) {
        block->set_frequency(frequency);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_SignalSource_nativeSetAmplitude(
        JNIEnv* env, jobject thiz, jlong handle, jdouble amplitude) {
    auto block = MicroDSP::get_instance().get_block<SignalSource>(handle);
    if (block) {
        block->set_amplitude(amplitude);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_SignalSource_nativeSetSignalType(
        JNIEnv* env, jobject thiz, jlong handle, jint signal_type) {
    auto block = MicroDSP::get_instance().get_block<SignalSource>(handle);
    if (block) {
        block->set_signal_type(static_cast<SignalType>(signal_type));
    }
}
