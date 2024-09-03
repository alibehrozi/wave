#include <jni.h>

#include <cstring>
#include <hackrf.h>

#include "HackRFTransfer.h"
#include "dataStream/NativeByteBuffer.h"
#include "dataStream/BuffersStorage.h"

#include "SimpleLog.h"

static jclass callbackClass;
static jobject globalCallback;
static jmethodID callbackMethod;

extern "C" {

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_open
        (JNIEnv *env, jclass object, jint file_descriptor) {
    return HackRFTransfer::getInstance().openConnection(file_descriptor);
}

JNIEXPORT jboolean JNICALL Java_com_github_alibehrozi_wave_Hackrf_isOpen
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().isDisconnected();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_close
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().dropConnection();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_startRx
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().startRx();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_stopRx
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().stopRx();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_startRecording
        (JNIEnv *env, jclass object, jstring path) {

    // Convert the Java string to a C-style string
    const char *nativePath = env->GetStringUTFChars(path, nullptr);

    // Check if GetStringUTFChars failed (returns nullptr if an exception occurred)
    if (nativePath == nullptr) {
        // Handle the error
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_error result = HackRFTransfer::getInstance().startRecording(nativePath);

    // Release the C-style string
    env->ReleaseStringUTFChars(path, nativePath);

    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_start_recording() failed: %s (%d)",
             hackrf_error_name(result), result);
        return result;
    }

    return HACKRF_SUCCESS;
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_stopRecording
        (JNIEnv *env, jclass object) {
    HackRFTransfer::getInstance().stopRecording();
    return HACKRF_SUCCESS;
}

JNIEXPORT jboolean JNICALL Java_com_github_alibehrozi_wave_Hackrf_isRecording
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().isRecording();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_startTx
        (JNIEnv *env, jclass object, jobject callback) {

    // Create a global reference to the callback object
    globalCallback = (*env).NewGlobalRef(callback);
    env->DeleteLocalRef(callback);
    callback = globalCallback;

    // Obtain a class reference for the callback interface
    callbackClass = (*env).GetObjectClass(callback);
    if (nullptr == callbackClass) {
        LOGE("callback method could not be found");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    // Find the method ID
    callbackMethod = (*env).GetMethodID(callbackClass, "onComplete", "()V");
    if (callbackMethod == nullptr) {
        LOGE("callback method could not be found");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    return HackRFTransfer::getInstance().startTx();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_stopTx
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().stopTx();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_getTransceiverMode
        (JNIEnv *env, jclass clazz) {
    return HackRFTransfer::getInstance().getTransceiverMode();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_setFrequency
        (JNIEnv *env, jclass object, jlong freq_hz) {
    return HackRFTransfer::getInstance().setFrequency(freq_hz);
}

JNIEXPORT jlong JNICALL Java_com_github_alibehrozi_wave_Hackrf_getFrequency
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().getFrequency();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_setSampleRate
        (JNIEnv *env, jclass object, jlong sampleRate_hz) {
    return HackRFTransfer::getInstance().setSampleRate(sampleRate_hz);
}

JNIEXPORT jlong JNICALL Java_com_github_alibehrozi_wave_Hackrf_getSampleRate
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().getSampleRate();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_setLNAGain
        (JNIEnv *env, jclass object, jint gain) {
    return HackRFTransfer::getInstance().setLNAGain(gain);
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_getLNAGain
        (JNIEnv *env, jclass clazz) {
    return HackRFTransfer::getInstance().getLNAGain();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_setVGAGain
        (JNIEnv *env, jclass object, jint gain) {
    return HackRFTransfer::getInstance().setVGAGain(gain);
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_getVGAGain
        (JNIEnv *env, jclass clazz) {
    return HackRFTransfer::getInstance().getVGAGain();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_setTxVGAGain
        (JNIEnv *env, jclass object, jint gain) {
    return HackRFTransfer::getInstance().setTxVGAGain(gain);
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_getTxVGAGain
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().getTxVGAGain();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_setAmpEnable
        (JNIEnv *env, jclass object, jboolean enable) {
    return HackRFTransfer::getInstance().setAmpEnable(enable);
}

JNIEXPORT jboolean JNICALL Java_com_github_alibehrozi_wave_Hackrf_isAmpEnable
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().isAmpEnable();
}

JNIEXPORT jint JNICALL Java_com_github_alibehrozi_wave_Hackrf_setAntennaEnable
        (JNIEnv *env, jclass object, jboolean enable) {
    return HackRFTransfer::getInstance().setAntennaEnable(enable);
}

JNIEXPORT jboolean JNICALL Java_com_github_alibehrozi_wave_Hackrf_isAntennaEnable
        (JNIEnv *env, jclass object) {
    return HackRFTransfer::getInstance().isAntennaEnable();
}

JNIEXPORT jlong JNICALL Java_com_github_alibehrozi_wave_Hackrf_getByteStream
        (JNIEnv *env, jclass object) {

    ByteStream *buffer = HackRFTransfer::getInstance().getByteStream();

    if (buffer == nullptr) {
        return 0;
    }

    return (jlong) buffer;
}

}