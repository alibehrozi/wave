#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "../../microdsp.h"
#include "AmDemodulator.h"
#include "FmDemodulator.h"
#include "SsbDemodulator.h"
#include "WfmDemodulator.h"
#include "FskDemodulator.h"
#include "BpskDemodulator.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Demodulation-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_demodulation_AmDemodulator_nativeCreateAmDemodulator(
        JNIEnv* env, jclass thiz, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<AmDemodulator>(c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_demodulation_FmDemodulator_nativeCreateFmDemodulator(
        JNIEnv* env, jclass thiz, jfloat gain, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<FmDemodulator>(gain, c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_demodulation_SsbDemodulator_nativeCreateSsbDemodulator(
        JNIEnv* env, jclass thiz, jint sideband, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<SsbDemodulator>(static_cast<SsbDemodulator::Sideband>(sideband), c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_demodulation_WfmDemodulator_nativeCreateWfmDemodulator(
        JNIEnv* env, jclass thiz, jdouble sample_rate, jdouble tau, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<WfmDemodulator>(sample_rate, tau, c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_demodulation_FskDemodulator_nativeCreateFskDemodulator(
        JNIEnv* env, jclass thiz, jdouble sample_rate, jdouble freq_mark, jdouble freq_space, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<FskDemodulator>(sample_rate, freq_mark, freq_space, c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_demodulation_BpskDemodulator_nativeCreateBpskDemodulator(
        JNIEnv* env, jclass thiz, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<BpskDemodulator>(c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_demodulation_FmDemodulator_nativeSetGain(
        JNIEnv* env, jobject thiz, jlong handle, jfloat gain) {
    auto block = MicroDSP::get_instance().get_block<FmDemodulator>(handle);
    if (block) {
        block->set_gain(gain);
    }
}
