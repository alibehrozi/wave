#include "SdrSink.h"
#include <jni.h>
#include <android/log.h>
#include "microdsp.h"

#define LOG_TAG "SdrSink-JNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_SdrSink_nativeCreateSdrSink(
        JNIEnv* env, jclass thiz, jlong device_handle, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (!c_name) return 0;

    try {
        SdrDevice* sdr_device = reinterpret_cast<SdrDevice*>(device_handle);
        if (!sdr_device) {
            env->ReleaseStringUTFChars(name, c_name);
            return 0;
        }

        std::shared_ptr<SdrDevice> sdr_shared(sdr_device, [](SdrDevice*){});

        auto sdr_sink = std::make_shared<SdrSink>(
                sdr_shared,
                DataType::COMPLEX_FLOAT, // Default to float for now
                c_name);

        jlong handle = MicroDSP::get_instance().register_block(sdr_sink);

        env->ReleaseStringUTFChars(name, c_name);
        return handle;

    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        LOGE("Exception creating SdrSink: %s", e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_SdrSink_nativeDestroySdrSink(
        JNIEnv* env, jclass thiz, jlong handle) {

    auto sdr_sink = MicroDSP::get_instance().get_block<SdrSink>(handle);
    if (sdr_sink) {
        sdr_sink->stop();
        MicroDSP::get_instance().unregister_object(handle);
    }
}

} // extern "C"
