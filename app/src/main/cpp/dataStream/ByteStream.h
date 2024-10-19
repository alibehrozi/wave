#ifndef BYTESTREAM_H
#define BYTESTREAM_H

#include <vector>
#include <stdint.h>

class NativeByteBuffer;

class ByteStream {
public:
    explicit ByteStream(bool threadSafe = true, size_t queue_size = 10);
    ~ByteStream();

    void append(NativeByteBuffer* buffer);
    bool hasData();
    void get(NativeByteBuffer *dst);
    NativeByteBuffer* getFirst();
    NativeByteBuffer* getLast();
    void discard(uint32_t count);
    void clean();
    uint32_t getNextBufferSize();

private:
    std::vector<NativeByteBuffer *> buffersQueue;
    bool isThreadSafe;
    size_t capacity;

    pthread_mutex_t mutex;
    pthread_cond_t cond;

    void lock();
    void unlock();
};

#endif