#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "../microdsp.h"
#include "sources/JavaSource.h"
#include "sinks/JavaSink.h"

#define LOG_TAG "JavaInterop-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// --- JavaSource JNI ---

JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_JavaSource_nativeCreateJavaSource(
        JNIEnv* env, jclass thiz, jint type, jint capacity, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<JavaSource>(static_cast<DataType>(type), static_cast<size_t>(capacity), c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_JavaSource_nativePush(
        JNIEnv* env, jobject thiz, jlong handle, jobject byteBuffer, jint count) {
    auto block = MicroDSP::get_instance().get_block<JavaSource>(handle);
    if (!block) return 0;

    void* ptr = env->GetDirectBufferAddress(byteBuffer);
    if (!ptr) return 0;

    return static_cast<jint>(block->push(ptr, static_cast<size_t>(count)));
}

JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_JavaSource_nativeWriteAvailable(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block<JavaSource>(handle);
    return block ? static_cast<jint>(block->write_available()) : 0;
}

// --- JavaSink JNI ---

JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_JavaSink_nativeCreateJavaSink(
        JNIEnv* env, jclass thiz, jint type, jint capacity, jstring name) {
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    auto block = std::make_shared<JavaSink>(static_cast<DataType>(type), static_cast<size_t>(capacity), c_name);
    jlong handle = MicroDSP::get_instance().register_block(block);
    env->ReleaseStringUTFChars(name, c_name);
    return handle;
}

JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_JavaSink_nativePull(
        JNIEnv* env, jobject thiz, jlong handle, jobject byteBuffer, jint maxCount) {
    auto block = MicroDSP::get_instance().get_block<JavaSink>(handle);
    if (!block) return 0;

    void* ptr = env->GetDirectBufferAddress(byteBuffer);
    if (!ptr) return 0;

    return static_cast<jint>(block->pull(ptr, static_cast<size_t>(maxCount)));
}

JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_JavaSink_nativeReadAvailable(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block<JavaSink>(handle);
    return block ? static_cast<jint>(block->read_available()) : 0;
}

} // extern "C"
