#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "../../microdsp.h"
#include "NoiseSource.h"

#define LOG_TAG "NoiseSource-JNI"
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
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_NoiseSource_nativeCreateNoiseSource(
        JNIEnv* env, jclass thiz, jint type, jfloat amplitude, jint noise_type, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) {
        return 0;
    }

    try {
        auto noise_source = std::make_shared<NoiseSource>(
                static_cast<DataType>(type),
                amplitude,
                static_cast<NoiseType>(noise_type),
                c_name);

        jlong handle = MicroDSP::get_instance().register_block(noise_source);
        env->ReleaseStringUTFChars(name, c_name);
        return handle;
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_NoiseSource_nativeSetAmplitude(
        JNIEnv* env, jobject thiz, jlong handle, jfloat amplitude) {
    auto block = MicroDSP::get_instance().get_block<NoiseSource>(handle);
    if (block) {
        block->set_amplitude(amplitude);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_NoiseSource_nativeSetNoiseType(
        JNIEnv* env, jobject thiz, jlong handle, jint noise_type) {
    auto block = MicroDSP::get_instance().get_block<NoiseSource>(handle);
    if (block) {
        block->set_noise_type(static_cast<NoiseType>(noise_type));
    }
}
