#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "../../microdsp.h"
#include "AmModulator.h"
#include "FmModulator.h"
#include "SsbModulator.h"
#include "FskModulator.h"
#include "BpskModulator.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Modulation-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_modulation_AmModulator_nativeCreateAmModulator(
        JNIEnv* env, jclass thiz, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<AmModulator>(c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_modulation_FmModulator_nativeCreateFmModulator(
        JNIEnv* env, jclass thiz, jfloat sensitivity, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<FmModulator>(sensitivity, c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_modulation_SsbModulator_nativeCreateSsbModulator(
        JNIEnv* env, jclass thiz, jint sideband, jint ntaps, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<SsbModulator>(static_cast<SsbModulator::Sideband>(sideband), ntaps, c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_modulation_FskModulator_nativeCreateFskModulator(
        JNIEnv* env, jclass thiz, jdouble sample_rate, jdouble freq_mark, jdouble freq_space, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<FskModulator>(sample_rate, freq_mark, freq_space, c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_modulation_BpskModulator_nativeCreateBpskModulator(
        JNIEnv* env, jclass thiz, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<BpskModulator>(c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_modulation_FmModulator_nativeSetSensitivity(
        JNIEnv* env, jobject thiz, jlong handle, jfloat sensitivity) {
    auto block = MicroDSP::get_instance().get_block<FmModulator>(handle);
    if (block) {
        block->set_sensitivity(sensitivity);
    }
}
