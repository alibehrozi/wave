#include <jni.h>
#include <string>
#include <memory>
#include <vector>
#include <android/log.h>

#include "../../microdsp.h"
#include "FirFilter.h"
#include "IirFilter.h"
#include "HilbertFilter.h"
#include "FilterDesign.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Filters-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void throwJavaException(JNIEnv* env, const char* className, const char* message) {
    jclass clazz = env->FindClass(className);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
    }
    env->DeleteLocalRef(clazz);
}

static jfloatArray vectorToJFloatArray(JNIEnv* env, const std::vector<float>& vec) {
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(vec.size()));
    if (result != nullptr && !vec.empty()) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(vec.size()), vec.data());
    }
    return result;
}

static jfloatArray biquadToJFloatArray(JNIEnv* env, const BiquadCoeffs& c) {
    float arr[5] = {c.b0, c.b1, c.b2, c.a1, c.a2};
    jfloatArray result = env->NewFloatArray(5);
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, 5, arr);
    }
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FirFilter_nativeCreateFirFilter(
        JNIEnv* env, jclass thiz, jint type, jfloatArray taps, jint decimation, jint interpolation, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) return 0;

    jsize taps_len = env->GetArrayLength(taps);
    jfloat* taps_elements = env->GetFloatArrayElements(taps, nullptr);
    std::vector<float> taps_vec(taps_elements, taps_elements + taps_len);
    env->ReleaseFloatArrayElements(taps, taps_elements, JNI_ABORT);

    try {
        auto fir = std::make_shared<FirFilter>(
                static_cast<DataType>(type),
                taps_vec,
                decimation,
                interpolation,
                c_name);

        jlong handle = MicroDSP::get_instance().register_block(fir);
        env->ReleaseStringUTFChars(name, c_name);
        return handle;
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FirFilter_nativeSetTaps(
        JNIEnv* env, jobject thiz, jlong handle, jfloatArray taps) {

    auto block = MicroDSP::get_instance().get_block<FirFilter>(handle);
    if (block) {
        jsize taps_len = env->GetArrayLength(taps);
        jfloat* taps_elements = env->GetFloatArrayElements(taps, nullptr);
        std::vector<float> taps_vec(taps_elements, taps_elements + taps_len);
        env->ReleaseFloatArrayElements(taps, taps_elements, JNI_ABORT);
        block->set_taps(taps_vec);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_IirFilter_nativeCreateIirFilter(
        JNIEnv* env, jclass thiz, jint type, jfloatArray sos_coeffs, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) return 0;

    jsize len = env->GetArrayLength(sos_coeffs);
    jfloat* elements = env->GetFloatArrayElements(sos_coeffs, nullptr);
    std::vector<float> coeffs_vec(elements, elements + len);
    env->ReleaseFloatArrayElements(sos_coeffs, elements, JNI_ABORT);

    try {
        auto iir = std::make_shared<IirFilter>(
                static_cast<DataType>(type),
                coeffs_vec,
                c_name);

        jlong handle = MicroDSP::get_instance().register_block(iir);
        env->ReleaseStringUTFChars(name, c_name);
        return handle;
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_IirFilter_nativeSetCoefficients(
        JNIEnv* env, jobject thiz, jlong handle, jfloatArray sos_coeffs) {

    auto block = MicroDSP::get_instance().get_block<IirFilter>(handle);
    if (block) {
        jsize len = env->GetArrayLength(sos_coeffs);
        jfloat* elements = env->GetFloatArrayElements(sos_coeffs, nullptr);
        std::vector<float> coeffs_vec(elements, elements + len);
        env->ReleaseFloatArrayElements(sos_coeffs, elements, JNI_ABORT);
        block->set_coefficients(coeffs_vec);
    }
}


extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeLowPass(
        JNIEnv* env, jclass thiz, jdouble gain, jdouble sampling_freq, jdouble cutoff_freq, jdouble transition_width, jint window_type) {
    std::vector<float> taps = FilterDesign::low_pass(gain, sampling_freq, cutoff_freq, transition_width, static_cast<FilterDesign::WindowType>(window_type));
    return vectorToJFloatArray(env, taps);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeHighPass(
        JNIEnv* env, jclass thiz, jdouble gain, jdouble sampling_freq, jdouble cutoff_freq, jdouble transition_width, jint window_type) {
    std::vector<float> taps = FilterDesign::high_pass(gain, sampling_freq, cutoff_freq, transition_width, static_cast<FilterDesign::WindowType>(window_type));
    return vectorToJFloatArray(env, taps);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBandPass(
        JNIEnv* env, jclass thiz, jdouble gain, jdouble sampling_freq, double low_cutoff, double high_cutoff, jdouble transition_width, jint window_type) {
    std::vector<float> taps = FilterDesign::band_pass(gain, sampling_freq, low_cutoff, high_cutoff, transition_width, static_cast<FilterDesign::WindowType>(window_type));
    return vectorToJFloatArray(env, taps);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBandReject(
        JNIEnv* env, jclass thiz, jdouble gain, jdouble sampling_freq, double low_cutoff, double high_cutoff, jdouble transition_width, jint window_type) {
    std::vector<float> taps = FilterDesign::band_reject(gain, sampling_freq, low_cutoff, high_cutoff, transition_width, static_cast<FilterDesign::WindowType>(window_type));
    return vectorToJFloatArray(env, taps);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeRootRaisedCosine(
        JNIEnv* env, jclass thiz, jdouble gain, jdouble sampling_freq, jdouble symbol_rate, jdouble excess_bw, jint ntaps) {
    std::vector<float> taps = FilterDesign::root_raised_cosine(gain, sampling_freq, symbol_rate, excess_bw, ntaps);
    return vectorToJFloatArray(env, taps);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeRaisedCosine(
        JNIEnv* env, jclass thiz, jdouble gain, jdouble sampling_freq, jdouble symbol_rate, jdouble excess_bw, jint ntaps) {
    std::vector<float> taps = FilterDesign::raised_cosine(gain, sampling_freq, symbol_rate, excess_bw, ntaps);
    return vectorToJFloatArray(env, taps);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeGaussian(
        JNIEnv* env, jclass thiz, jdouble gain, jdouble sampling_freq, jdouble symbol_rate, jdouble bt, jint ntaps) {
    std::vector<float> taps = FilterDesign::gaussian(gain, sampling_freq, symbol_rate, bt, ntaps);
    return vectorToJFloatArray(env, taps);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeHilbert(
        JNIEnv* env, jclass thiz, jint ntaps, jint window_type) {
    std::vector<float> taps = FilterDesign::hilbert(ntaps, static_cast<FilterDesign::WindowType>(window_type));
    return vectorToJFloatArray(env, taps);
}


extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBiquadLowPass(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble cutoff_freq, jdouble q) {
    BiquadCoeffs c = FilterDesign::biquad_low_pass(sampling_freq, cutoff_freq, q);
    return biquadToJFloatArray(env, c);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBiquadHighPass(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble cutoff_freq, jdouble q) {
    BiquadCoeffs c = FilterDesign::biquad_high_pass(sampling_freq, cutoff_freq, q);
    return biquadToJFloatArray(env, c);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBiquadBandPass(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble center_freq, jdouble q) {
    BiquadCoeffs c = FilterDesign::biquad_band_pass(sampling_freq, center_freq, q);
    return biquadToJFloatArray(env, c);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBiquadNotch(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble center_freq, jdouble q) {
    BiquadCoeffs c = FilterDesign::biquad_notch(sampling_freq, center_freq, q);
    return biquadToJFloatArray(env, c);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBiquadPeaking(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble center_freq, jdouble q, jdouble gain_db) {
    BiquadCoeffs c = FilterDesign::biquad_peaking(sampling_freq, center_freq, q, gain_db);
    return biquadToJFloatArray(env, c);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBiquadLowShelf(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble cutoff_freq, jdouble q, jdouble gain_db) {
    BiquadCoeffs c = FilterDesign::biquad_low_shelf(sampling_freq, cutoff_freq, q, gain_db);
    return biquadToJFloatArray(env, c);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeBiquadHighShelf(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble cutoff_freq, jdouble q, jdouble gain_db) {
    BiquadCoeffs c = FilterDesign::biquad_high_shelf(sampling_freq, cutoff_freq, q, gain_db);
    return biquadToJFloatArray(env, c);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeButterworthLowPass(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble cutoff_freq, jint order) {
    auto sos = FilterDesign::butterworth_low_pass(sampling_freq, cutoff_freq, order);
    auto flat = FilterDesign::sos_to_flat_vector(sos);
    return vectorToJFloatArray(env, flat);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_FilterDesign_nativeButterworthHighPass(
        JNIEnv* env, jclass thiz, jdouble sampling_freq, jdouble cutoff_freq, jint order) {
    auto sos = FilterDesign::butterworth_high_pass(sampling_freq, cutoff_freq, order);
    auto flat = FilterDesign::sos_to_flat_vector(sos);
    return vectorToJFloatArray(env, flat);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_filters_HilbertFilter_nativeCreateHilbertFilter(
        JNIEnv* env, jclass thiz, jint ntaps, jstring name) {

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) return 0;

    try {
        auto hilbert = std::make_shared<HilbertFilter>(ntaps, c_name);
        jlong handle = MicroDSP::get_instance().register_block(hilbert);
        env->ReleaseStringUTFChars(name, c_name);
        return handle;
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return 0;
    }
}

