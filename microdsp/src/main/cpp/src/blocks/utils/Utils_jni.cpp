#include <jni.h>
#include <string>
#include <memory>
#include <vector>
#include <android/log.h>

#include "../../microdsp.h"
#include "RationalResampler.h"
#include "AgcBlock.h"
#include "SquelchBlock.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Utils-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_utils_RationalResampler_nativeCreateRationalResampler(
        JNIEnv* env, jclass thiz, jint type, jint interpolation, jint decimation, jfloatArray taps, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    jsize taps_len = env->GetArrayLength(taps);
    jfloat* taps_elements = env->GetFloatArrayElements(taps, nullptr);
    std::vector<float> taps_vec(taps_elements, taps_elements + taps_len);
    env->ReleaseFloatArrayElements(taps, taps_elements, JNI_ABORT);

    auto block = std::make_shared<RationalResampler>(
            static_cast<DataType>(type),
            interpolation,
            decimation,
            taps_vec,
            c_name);

    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_utils_AgcBlock_nativeCreateAgcBlock(
        JNIEnv* env, jclass thiz, jint type, jfloat target_level, jfloat attack_rate, jfloat decay_rate, jfloat max_gain, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<AgcBlock>(
            static_cast<DataType>(type),
            target_level,
            attack_rate,
            decay_rate,
            max_gain,
            c_name);

    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_utils_AgcBlock_nativeSetTargetLevel(
        JNIEnv* env, jobject thiz, jlong handle, jfloat level) {
    auto block = MicroDSP::get_instance().get_block<AgcBlock>(handle);
    if (block) block->set_target_level(level);
}

// --- SquelchBlock JNI ---

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_utils_SquelchBlock_nativeCreateSquelchBlock(
        JNIEnv* env, jclass thiz, jint type, jfloat threshold_db, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<SquelchBlock>(static_cast<DataType>(type), threshold_db, c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_utils_SquelchBlock_nativeSetThreshold(
        JNIEnv* env, jobject thiz, jlong handle, jfloat db) {
    auto block = MicroDSP::get_instance().get_block<SquelchBlock>(handle);
    if (block) block->set_threshold(db);
}
