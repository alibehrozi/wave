#include "ByteStream.h"
#include "NativeByteBuffer.h"

ByteStream::ByteStream() {

}

ByteStream::~ByteStream() {

}

void ByteStream::append(NativeByteBuffer *buffer) {
    if (buffer == nullptr) {
        return;
    }
    buffersQueue.push_back(buffer);
}

bool ByteStream::hasData() {
    size_t size = buffersQueue.size();
    for (uint32_t a = 0; a < size; a++) {
        if (buffersQueue[a]->hasRemaining()) {
            return true;
        }
    }
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
    return buffersQueue[0];
}

NativeByteBuffer *ByteStream::getLast() {
    return buffersQueue[buffersQueue.size()];
}

void ByteStream::discard(uint32_t count) {
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
}

void ByteStream::clean() {
    if (buffersQueue.empty()) {
        return;
    }
    size_t size = buffersQueue.size();
    for (uint32_t a = 0; a < size; a++) {
        NativeByteBuffer *buffer = buffersQueue[a];
        buffer->reuse();
    }
    buffersQueue.clear();
}

uint32_t ByteStream::getNextBufferSize() {
    if (!buffersQueue.empty()) {
        return buffersQueue.front()->remaining();
    } else {
        return 0;
    }
}
