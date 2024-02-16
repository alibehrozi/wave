#include <jni.h>
#include "BuffersStorage.h"
#include "NativeByteBuffer.h"
#include "SimpleLog.h"

static JavaVM *java;

extern "C" {

jlong Java_com_github_alibehrozi_wave_dataStream_NativeByteBuffer_getFreeBuffer
        (JNIEnv *env, jclass object, jint length) {
    return (jlong) (intptr_t) BuffersStorage::getInstance().getFreeBuffer((uint32_t) length);
}

jlong Java_com_github_alibehrozi_wave_dataStream_NativeByteBuffer_limit
        (JNIEnv *env, jclass object, jlong address) {
    auto *buffer = (NativeByteBuffer *) (intptr_t) address;
    return buffer->limit();
}

jlong Java_com_github_alibehrozi_wave_dataStream_NativeByteBuffer_position
        (JNIEnv *env, jclass object, jlong address) {
    auto *buffer = (NativeByteBuffer *) (intptr_t) address;
    return buffer->position();
}

void Java_com_github_alibehrozi_wave_dataStream_NativeByteBuffer_reuse
        (JNIEnv *env, jclass object, jlong address) {
    auto *buffer = (NativeByteBuffer *) (intptr_t) address;
    buffer->reuse();
}

jobject Java_com_github_alibehrozi_wave_dataStream_NativeByteBuffer_getJavaByteBuffer
        (JNIEnv *env, jclass object, jlong address) {
    auto *buffer = (NativeByteBuffer *) (intptr_t) address;
    if (buffer == nullptr) {
        return nullptr;
    }
    return buffer->getJavaByteBuffer();
}

void Java_com_github_alibehrozi_wave_dataStream_NativeByteBuffer_setJava
        (JNIEnv *env, jclass object, jboolean useJavaByteBuffers) {
    env->GetJavaVM(&java);
    NativeByteBuffer::useJavaVM(java, useJavaByteBuffers);
}

}