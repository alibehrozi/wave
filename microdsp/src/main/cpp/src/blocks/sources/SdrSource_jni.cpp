#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "microdsp.h"
#include "SdrSource.h"
#include "hardware/sdr.h"

#define LOG_TAG "SdrSource-JNI"
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
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_SdrSource_nativeCreateSdrSource(
        JNIEnv* env, jclass thiz, jlong device_handle, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) {
        throwJavaException(env, "java/lang/OutOfMemoryError", "Failed to get string chars");
        return 0;
    }

    try {
        SdrDevice* sdr_device = reinterpret_cast<SdrDevice*>(device_handle);
        if (!sdr_device) {
            env->ReleaseStringUTFChars(name, c_name);
            LOGE("Failed to get Sdr device from handle: %lld", (long long)device_handle);
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Invalid SDR device handle");
            return 0;
        }

        std::shared_ptr<SdrDevice> sdr_shared(sdr_device, [](SdrDevice*){});

        auto sdr_source = std::make_shared<SdrSource>(
                sdr_shared,
                c_name);

        jlong handle = MicroDSP::get_instance().register_block(sdr_source);

        env->ReleaseStringUTFChars(name, c_name);
        LOGI("Created SdrSource: %s, handle: %lld, device: %lld",
             c_name, (long long)handle, (long long)device_handle);
        return handle;

    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        LOGE("Exception creating SdrSource: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_SdrSource_nativeDestroySdrSource(
        JNIEnv* env, jclass thiz, jlong handle) {

    auto sdr_source = MicroDSP::get_instance().get_block<SdrSource>(handle);
    if (sdr_source) {
        sdr_source->stop();
        MicroDSP::get_instance().unregister_object(handle);
        LOGI("Destroyed SdrSource handle: %lld", (long long)handle);
    } else {
        LOGE("Failed to destroy SdrSource - invalid handle: %lld", (long long)handle);
    }
}
