#include <jni.h>
#include "ByteStream.h"
#include "BuffersStorage.h"


extern "C" {

JNIEXPORT void JNICALL Java_com_github_alibehrozi_wave_dataStream_ByteBufferStream_appendBytes
(JNIEnv *env, jclass object,jlong streamAddress, jlong bufferAddress) {
    auto *stream = (ByteStream *) (intptr_t) streamAddress;
    auto *buff = (NativeByteBuffer *) (intptr_t) bufferAddress;
    stream->append(buff);
}

JNIEXPORT jboolean JNICALL Java_com_github_alibehrozi_wave_dataStream_ByteBufferStream_hasData
        (JNIEnv *env, jclass object, jlong streamAddress) {
    auto *stream = (ByteStream *) (intptr_t) streamAddress;
    return stream->hasData();
}

JNIEXPORT void JNICALL Java_com_github_alibehrozi_wave_dataStream_ByteBufferStream_getBuffer
        (JNIEnv *env, jclass object, jlong streamAddress, jlong bufferAddress) {
    auto *stream = (ByteStream *) (intptr_t) streamAddress;
    auto *buffer = (NativeByteBuffer *) (intptr_t) bufferAddress;
    stream->get(buffer);
}


JNIEXPORT jlong JNICALL Java_com_github_alibehrozi_wave_dataStream_ByteBufferStream_getData
        (JNIEnv *env, jclass object, jlong streamAddress) {
    auto *stream = (ByteStream *) (intptr_t) streamAddress;
    uint32_t bufferSize = stream->getNextBufferSize();
    auto *buffer = BuffersStorage::getInstance().getFreeBuffer(bufferSize);
    stream->get(buffer);
    return (intptr_t) buffer;
}

JNIEXPORT void JNICALL Java_com_github_alibehrozi_wave_dataStream_ByteBufferStream_discardBytes
        (JNIEnv *env, jclass object, jlong streamAddress, jint count) {
    auto *stream = (ByteStream *) (intptr_t) streamAddress;
    stream->discard(count);
}

JNIEXPORT void JNICALL Java_com_github_alibehrozi_wave_dataStream_ByteBufferStream_remove
        (JNIEnv *env, jclass object, jlong streamAddress) {
    auto *stream = (ByteStream *) (intptr_t) streamAddress;
    stream->clean();
}

}