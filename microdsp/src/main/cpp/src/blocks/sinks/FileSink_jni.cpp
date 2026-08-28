#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "microdsp.h"
#include "FileSink.h"

#define LOG_TAG "FileSink-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * @brief Throw a Java exception with the given message
 */
static void throwJavaException(JNIEnv* env, const char* className, const char* message) {
    jclass clazz = env->FindClass(className);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
    }
    env->DeleteLocalRef(clazz);
}

/**
 * @brief Get a shared_ptr from a handle with null check
 */
template<typename T>
static std::shared_ptr<T> getBlockFromHandle(JNIEnv* env, jlong handle, const char* context) {
    auto block = MicroDSP::get_instance().get_block<T>(handle);
    if (!block) {
        LOGE("%s - invalid handle: %lld", context, (long long)handle);
        throwJavaException(env, "java/lang/IllegalStateException",
                           (std::string(context) + " - invalid handle").c_str());
        return nullptr;
    }
    return block;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeCreateFileSink(
        JNIEnv* env, jobject thiz, jint data_type, jstring filename, jint mode, jstring name) {

    if (filename == nullptr) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Filename cannot be null");
        return 0;
    }

    if (name == nullptr) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Name cannot be null");
        return 0;
    }

    const char* c_filename = env->GetStringUTFChars(filename, nullptr);
    if (c_filename == nullptr) {
        throwJavaException(env, "java/lang/OutOfMemoryError", "Failed to get filename chars");
        return 0;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr) {
        env->ReleaseStringUTFChars(filename, c_filename);
        throwJavaException(env, "java/lang/OutOfMemoryError", "Failed to get name chars");
        return 0;
    }

    try {
        DataType cpp_data_type = static_cast<DataType>(data_type);
        FileSink::FileMode cpp_mode = static_cast<FileSink::FileMode>(mode);

        auto file_sink = std::make_shared<FileSink>(
                cpp_data_type,
                c_filename,
                cpp_mode,
                c_name,
                2097152); // 2MB buffer

        jlong handle = MicroDSP::get_instance().register_block(file_sink);

        env->ReleaseStringUTFChars(filename, c_filename);
        env->ReleaseStringUTFChars(name, c_name);

        LOGI("Created FileSink: %s, filename: %s, handle: %lld", c_name, c_filename, (long long)handle);
        return handle;

    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(filename, c_filename);
        env->ReleaseStringUTFChars(name, c_name);
        LOGE("Exception creating FileSink: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeDestroyFileSink(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = MicroDSP::get_instance().get_block<FileSink>(handle);
    if (file_sink) {
        file_sink->stop();
        file_sink->close();
        MicroDSP::get_instance().unregister_object(handle);
        LOGI("Destroyed FileSink handle: %lld", (long long)handle);
    } else {
        LOGE("Failed to destroy FileSink - invalid handle: %lld", (long long)handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeSetFilename(
        JNIEnv* env, jobject thiz, jlong handle, jstring filename) {

    if (filename == nullptr) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Filename cannot be null");
        return;
    }

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "set_filename");
    if (!file_sink) return;

    const char* c_filename = env->GetStringUTFChars(filename, nullptr);
    if (c_filename == nullptr) {
        throwJavaException(env, "java/lang/OutOfMemoryError", "Failed to get filename chars");
        return;
    }

    try {
        file_sink->set_filename(c_filename);
        env->ReleaseStringUTFChars(filename, c_filename);
        LOGI("FileSink set_filename: %s for handle: %lld", c_filename, (long long)handle);
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(filename, c_filename);
        LOGE("FileSink set_filename exception: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeSetMode(
        JNIEnv* env, jobject thiz, jlong handle, jint mode) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "set_mode");
    if (!file_sink) return;

    try {
        FileSink::FileMode cpp_mode = static_cast<FileSink::FileMode>(mode);
        file_sink->set_mode(cpp_mode);
        LOGI("FileSink set_mode: %d for handle: %lld", mode, (long long)handle);
    } catch (const std::exception& e) {
        LOGE("FileSink set_mode exception: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeSetMaxFileSize(
        JNIEnv* env, jobject thiz, jlong handle, jlong max_size) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "set_max_file_size");
    if (!file_sink) return;

    if (max_size < 0) {
        throwJavaException(env, "java/lang/IllegalArgumentException",
                           "Max file size cannot be negative");
        return;
    }

    file_sink->set_max_file_size(static_cast<size_t>(max_size));
    LOGI("FileSink set_max_file_size: %ld for handle: %lld", max_size, (long long)handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeStartRecording(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "start_recording");
    if (!file_sink) return;

    try {
        file_sink->start_recording();
        LOGI("FileSink start_recording for handle: %lld", (long long)handle);
    } catch (const std::exception& e) {
        LOGE("FileSink start_recording exception: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeStopRecording(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "stop_recording");
    if (!file_sink) return;

    try {
        file_sink->stop_recording();
        LOGI("FileSink stop_recording for handle: %lld", (long long)handle);
    } catch (const std::exception& e) {
        LOGE("FileSink stop_recording exception: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", e.what());
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeIsRecording(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "is_recording");
    if (!file_sink) return JNI_FALSE;

    return file_sink->is_recording() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeSetUnbuffered(
        JNIEnv* env, jobject thiz, jlong handle, jboolean unbuffered) {

    auto file_sink = MicroDSP::get_instance().get_block<FileSink>(handle);
    if (file_sink) {
        // Note: set_unbuffered method needs to be implemented in FileSink
        // file_sink->set_unbuffered(unbuffered == JNI_TRUE);
        LOGI("FileSink set_unbuffered: %s for handle: %lld (not implemented)",
             unbuffered ? "true" : "false", (long long)handle);
    } else {
        LOGE("FileSink set_unbuffered failed - invalid handle: %lld", (long long)handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeFlush(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "flush");
    if (!file_sink) return;

    file_sink->flush();
    LOGI("FileSink flush for handle: %lld", (long long)handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeClose(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "close");
    if (!file_sink) return;

    file_sink->close();
    LOGI("FileSink close for handle: %lld", (long long)handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeReopen(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "reopen");
    if (!file_sink) return;

    file_sink->reopen();
    LOGI("FileSink reopen for handle: %lld", (long long)handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeGetBytesWritten(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "get_bytes_written");
    if (!file_sink) return 0;

    long bytes_written = file_sink->get_bytes_written();
    LOGI("FileSink get_bytes_written: %ld for handle: %lld", bytes_written, (long long)handle);
    return bytes_written;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeIsOpen(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "is_open");
    if (!file_sink) return JNI_FALSE;

    bool is_open = file_sink->is_open();
    LOGI("FileSink is_open: %s for handle: %lld", is_open ? "true" : "false", (long long)handle);
    return is_open ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_alibehrozi_wave_microdsp_blocks_sinks_FileSink_nativeGetFilename(
        JNIEnv* env, jobject thiz, jlong handle) {

    auto file_sink = getBlockFromHandle<FileSink>(env, handle, "get_filename");
    if (!file_sink) return env->NewStringUTF("");

    std::string filename = file_sink->get_filename();
    LOGI("FileSink get_filename: %s for handle: %lld", filename.c_str(), (long long)handle);
    return env->NewStringUTF(filename.c_str());
}
