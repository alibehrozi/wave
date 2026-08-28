#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "HackRFDevice.h"

#define LOG_TAG "HackRfNative-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define JNI_FUNC(name) Java_com_github_alibehrozi_wave_microdsp_hardware_sdr_drivers_hackrf_HackRfNative_##name

extern "C" {

JNIEXPORT jlong JNICALL JNI_FUNC(create)(JNIEnv* env, jclass clazz, jint fd) {
    try {
        auto* sdr = new HackRfDevice();
        if (sdr->connect(fd) != 0) {
            delete sdr;
            return 0;
        }
        return reinterpret_cast<jlong>(sdr);
    } catch (const std::exception& e) {
        LOGE("Exception in create: %s", e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL JNI_FUNC(close)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    if (sdr) {
        sdr->close();
        delete sdr;
    }
}

JNIEXPORT jboolean JNICALL JNI_FUNC(isConnected)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->isConnected() : JNI_FALSE;
}

JNIEXPORT jint JNICALL JNI_FUNC(reset)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->reset() : -1;
}

JNIEXPORT jint JNICALL JNI_FUNC(setFrequency)(JNIEnv* env, jclass clazz, jlong handle, jlong freqHz) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->setFrequency(static_cast<long>(freqHz)) : -1;
}

JNIEXPORT jlong JNICALL JNI_FUNC(getFrequency)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? static_cast<jlong>(sdr->getFrequency()) : 0;
}

JNIEXPORT jint JNICALL JNI_FUNC(setSampleRate)(JNIEnv* env, jclass clazz, jlong handle, jlong rateHz) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->setSampleRate(static_cast<long>(rateHz)) : -1;
}

JNIEXPORT jlong JNICALL JNI_FUNC(getSampleRate)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? static_cast<jlong>(sdr->getSampleRate()) : 0;
}

JNIEXPORT jint JNICALL JNI_FUNC(setGain)(JNIEnv* env, jclass clazz, jlong handle, jint gainDb) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->setGain(gainDb) : -1;
}

JNIEXPORT jint JNICALL JNI_FUNC(getGain)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->getGain() : 0;
}

JNIEXPORT jint JNICALL JNI_FUNC(setLnaGain)(JNIEnv* env, jclass clazz, jlong handle, jint gainDb) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->setLnaGain(gainDb) : -1;
}

JNIEXPORT jint JNICALL JNI_FUNC(getLnaGain)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->getLnaGain() : 0;
}

JNIEXPORT jint JNICALL JNI_FUNC(setVgaGain)(JNIEnv* env, jclass clazz, jlong handle, jint gainDb) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->setVgaGain(gainDb) : -1;
}

JNIEXPORT jint JNICALL JNI_FUNC(getVgaGain)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->getVgaGain() : 0;
}

JNIEXPORT jint JNICALL JNI_FUNC(setAmpEnabled)(JNIEnv* env, jclass clazz, jlong handle, jboolean enabled) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->setAmpEnabled(enabled == JNI_TRUE) : -1;
}

JNIEXPORT jboolean JNICALL JNI_FUNC(isAmpEnabled)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? (sdr->isAmpEnabled() ? JNI_TRUE : JNI_FALSE) : JNI_FALSE;
}

JNIEXPORT jint JNICALL JNI_FUNC(startRx)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->startRx() : -1;
}

JNIEXPORT jint JNICALL JNI_FUNC(stopRx)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->stopRx() : -1;
}

JNIEXPORT jint JNICALL JNI_FUNC(startTx)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->startTx() : -1;
}

JNIEXPORT jint JNICALL JNI_FUNC(stopTx)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    return sdr ? sdr->stopTx() : -1;
}

JNIEXPORT jint JNICALL JNI_FUNC(getStreamingState)(JNIEnv* env, jclass clazz, jlong handle) {
    auto* sdr = reinterpret_cast<HackRfDevice*>(handle);
    if (!sdr) return 0; // IDLE

    switch (sdr->getStreamingState()) {
        case SdrStreamingState::RX: return 1;
        case SdrStreamingState::TX: return 2;
        case SdrStreamingState::IDLE:
        default: return 0;
    }
}

} // extern "C"
