#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "../../microdsp.h"
#include "Add.h"
#include "AddConst.h"
#include "Multiply.h"
#include "MultiplyConst.h"
#include "Subtract.h"
#include "Divide.h"
#include "Abs.h"
#include "ComplexToMag.h"
#include "ComplexToMagSquared.h"
#include "ComplexToRealImag.h"
#include "RealImagToComplex.h"
#include "Conjugate.h"
#include "Log10.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Math-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// ==========================================
// 1. Add JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Add_nativeCreateAdd(
        JNIEnv* env, jclass clazz, jint type, jint num_inputs, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<Add>(
            static_cast<DataType>(type),
            static_cast<size_t>(num_inputs),
            c_name ? c_name : "add");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 2. AddConst JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_AddConst_nativeCreateAddConst(
        JNIEnv* env, jclass clazz, jint type, jfloat const_real, jfloat const_imag, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<AddConst>(
            static_cast<DataType>(type),
            const_real,
            const_imag,
            c_name ? c_name : "add_const");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_AddConst_nativeSetConstant(
        JNIEnv* env, jobject thiz, jlong handle, jfloat const_real, jfloat const_imag) {
    auto block = MicroDSP::get_instance().get_block<AddConst>(handle);
    if (block) block->set_constant(const_real, const_imag);
}

// ==========================================
// 3. Multiply JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Multiply_nativeCreateMultiply(
        JNIEnv* env, jclass clazz, jint type, jint num_inputs, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<Multiply>(
            static_cast<DataType>(type),
            static_cast<size_t>(num_inputs),
            c_name ? c_name : "multiply");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 4. MultiplyConst JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_MultiplyConst_nativeCreateMultiplyConst(
        JNIEnv* env, jclass clazz, jint type, jfloat const_real, jfloat const_imag, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<MultiplyConst>(
            static_cast<DataType>(type),
            const_real,
            const_imag,
            c_name ? c_name : "multiply_const");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_MultiplyConst_nativeSetConstant(
        JNIEnv* env, jobject thiz, jlong handle, jfloat const_real, jfloat const_imag) {
    auto block = MicroDSP::get_instance().get_block<MultiplyConst>(handle);
    if (block) block->set_constant(const_real, const_imag);
}

// ==========================================
// 5. Subtract JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Subtract_nativeCreateSubtract(
        JNIEnv* env, jclass clazz, jint type, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<Subtract>(
            static_cast<DataType>(type),
            c_name ? c_name : "subtract");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 6. Divide JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Divide_nativeCreateDivide(
        JNIEnv* env, jclass clazz, jint type, jfloat eps, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<Divide>(
            static_cast<DataType>(type),
            eps,
            c_name ? c_name : "divide");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Divide_nativeSetEps(
        JNIEnv* env, jobject thiz, jlong handle, jfloat eps) {
    auto block = MicroDSP::get_instance().get_block<Divide>(handle);
    if (block) block->set_eps(eps);
}

// ==========================================
// 7. Abs JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Abs_nativeCreateAbs(
        JNIEnv* env, jclass clazz, jint type, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<Abs>(
            static_cast<DataType>(type),
            c_name ? c_name : "abs");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 8. ComplexToMag JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_ComplexToMag_nativeCreateComplexToMag(
        JNIEnv* env, jclass clazz, jint type, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<ComplexToMag>(
            static_cast<DataType>(type),
            c_name ? c_name : "complex_to_mag");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 9. ComplexToMagSquared JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_ComplexToMagSquared_nativeCreateComplexToMagSquared(
        JNIEnv* env, jclass clazz, jint type, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<ComplexToMagSquared>(
            static_cast<DataType>(type),
            c_name ? c_name : "complex_to_mag_squared");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 10. ComplexToRealImag JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_ComplexToRealImag_nativeCreateComplexToRealImag(
        JNIEnv* env, jclass clazz, jint type, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<ComplexToRealImag>(
            static_cast<DataType>(type),
            c_name ? c_name : "complex_to_real_imag");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 11. RealImagToComplex JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_RealImagToComplex_nativeCreateRealImagToComplex(
        JNIEnv* env, jclass clazz, jint type, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<RealImagToComplex>(
            static_cast<DataType>(type),
            c_name ? c_name : "real_imag_to_complex");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 12. Conjugate JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Conjugate_nativeCreateConjugate(
        JNIEnv* env, jclass clazz, jint type, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<Conjugate>(
            static_cast<DataType>(type),
            c_name ? c_name : "conjugate");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

// ==========================================
// 13. Log10 JNI
// ==========================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Log10_nativeCreateLog10(
        JNIEnv* env, jclass clazz, jint type, jfloat n, jfloat k, jfloat eps, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<Log10>(
            static_cast<DataType>(type),
            n,
            k,
            eps,
            c_name ? c_name : "log10");
    jlong handle = MicroDSP::get_instance().register_block(block);
    if (c_name) env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_math_Log10_nativeSetParameters(
        JNIEnv* env, jobject thiz, jlong handle, jfloat n, jfloat k) {
    auto block = MicroDSP::get_instance().get_block<Log10>(handle);
    if (block) block->set_parameters(n, k);
}
