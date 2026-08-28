#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "microdsp.h"
#include "FileSource.h"

#define LOG_TAG "FileSource-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_FileSource_nativeCreateFileSource(
        JNIEnv* env, jclass thiz, jint data_type, jstring filename, jboolean repeat, jstring name) {

    const char* c_filename = env->GetStringUTFChars(filename, nullptr);
    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_filename == nullptr || c_name == nullptr) return 0;

    try {
        auto file_source = std::make_shared<FileSource>(
                static_cast<DataType>(data_type),
                c_filename,
                repeat == JNI_TRUE,
                c_name);

        jlong handle = MicroDSP::get_instance().register_block(file_source);

        env->ReleaseStringUTFChars(filename, c_filename);
        env->ReleaseStringUTFChars(name, c_name);
        LOGI("Created FileSource: %s, handle: %lld", c_name, (long long)handle);
        return handle;

    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(filename, c_filename);
        env->ReleaseStringUTFChars(name, c_name);
        LOGE("Exception creating FileSource: %s", e.what());
        jclass clazz = env->FindClass("java/lang/RuntimeException");
        if (clazz) env->ThrowNew(clazz, e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_FileSource_nativeSeek(
        JNIEnv* env, jclass thiz, jlong handle, jlong position) {
    auto block = MicroDSP::get_instance().get_block<FileSource>(handle);
    if (block) {
        block->seek(static_cast<size_t>(position));
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_FileSource_nativeGetFileSize(
        JNIEnv* env, jclass thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block<FileSource>(handle);
    return block ? static_cast<jlong>(block->get_file_size()) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sources_FileSource_nativeGetPosition(
        JNIEnv* env, jclass thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block<FileSource>(handle);
    return block ? static_cast<jlong>(block->get_position()) : 0;
}
