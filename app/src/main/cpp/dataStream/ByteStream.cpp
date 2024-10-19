#include "ByteStream.h"
#include "NativeByteBuffer.h"
#include "SimpleLog.h"
#include <pthread.h>

ByteStream::ByteStream(bool threadSafe, size_t queue_size)
        : isThreadSafe(threadSafe), capacity(queue_size) {
    buffersQueue.reserve(capacity);
    if (isThreadSafe) {
        pthread_mutex_init(&mutex, nullptr);
        pthread_cond_init(&cond, nullptr);
    }
}

ByteStream::~ByteStream() {
    if (isThreadSafe) {
        pthread_mutex_destroy(&mutex);
        pthread_cond_destroy(&cond);
    }
    buffersQueue.clear();
    buffersQueue.shrink_to_fit();
}

void ByteStream::lock() {
    if (isThreadSafe) pthread_mutex_lock(&mutex);
}

void ByteStream::unlock() {
    if (isThreadSafe) pthread_mutex_unlock(&mutex);
}

void ByteStream::append(NativeByteBuffer *buffer) {
    if (buffer == nullptr) {
        return;
    }

    lock();
    while (buffersQueue.size() >= capacity) {
        LOGI("Queue full, waiting...\n");
        // Wait until space becomes available
        pthread_cond_wait(&cond, &mutex);
    }

    buffersQueue.push_back(buffer);
    // Notify any waiting consumers
    pthread_cond_signal(&cond);
    unlock();
}

bool ByteStream::hasData() {
    lock();
    size_t size = buffersQueue.size();
    for (uint32_t a = 0; a < size; a++) {
        if (buffersQueue[a]->hasRemaining()) {
            return true;
            unlock();
        }
    }
    unlock();
    return false;
}

void ByteStream::get(NativeByteBuffer *dst) {
    if (dst == nullptr) {
        return;
    }

    size_t size = buffersQueue.size();
    NativeByteBuffer *buffer;
    for (uint32_t a = 0; a < size; a++) {
        buffer = buffersQueue[a];
        if (buffer->remaining() > dst->remaining()) {
            dst->writeBytes(buffer->bytes(), buffer->position(), dst->remaining());
            break;
        }
        dst->writeBytes(buffer->bytes(), buffer->position(), buffer->remaining());
        if (!dst->hasRemaining()) {
            break;
        }
    }
}

NativeByteBuffer *ByteStream::getFirst() {
    lock();
    while (buffersQueue.empty()) {
        LOGI("Queue empty, waiting...\n");
        pthread_cond_wait(&cond, &mutex);  // Wait until data becomes available
    }

    NativeByteBuffer *buffer = buffersQueue.front();
    unlock();
    return buffer;
}

NativeByteBuffer *ByteStream::getLast() {
    lock();
    while (buffersQueue.empty()) {
        LOGI("Queue empty, waiting...\n");
        pthread_cond_wait(&cond, &mutex);  // Wait until data becomes available
    }

    NativeByteBuffer *buffer = buffersQueue.back();
    unlock();
    return buffer;
}

void ByteStream::discard(uint32_t count) {
    lock();
    uint32_t remaining;
    NativeByteBuffer *buffer;
    while (count > 0) {
        if (buffersQueue.empty()) {
            break;
        }
        buffer = buffersQueue[0];
        remaining = buffer->remaining();
        if (count < remaining) {
            buffer->position(buffer->position() + count);
            break;
        }
        buffer->reuse();
        buffersQueue.erase(buffersQueue.begin());
        count -= remaining;
    }
    // Notify waiting producers if any
    pthread_cond_signal(&cond);
    unlock();
}

void ByteStream::clean() {
    lock();
    if (buffersQueue.empty()) {
        unlock();
        return;
    }
    size_t size = buffersQueue.size();
    for (uint32_t a = 0; a < size; a++) {
        NativeByteBuffer *buffer = buffersQueue[a];
        buffer->reuse();
    }
    buffersQueue.clear();
    unlock();
}

uint32_t ByteStream::getNextBufferSize() {
    lock();
    if (buffersQueue.empty()) {
        unlock();
        return 0;
    }

    unlock();
    return buffersQueue.front()->remaining();
}
