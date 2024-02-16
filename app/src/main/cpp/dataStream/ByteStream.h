#ifndef BYTESTREAM_H
#define BYTESTREAM_H

#include <vector>
#include <stdint.h>

class NativeByteBuffer;

class ByteStream {

public:
    ByteStream();
    ~ByteStream();
    void append(NativeByteBuffer *buffer);
    bool hasData();
    void get(NativeByteBuffer *dst);
    NativeByteBuffer * getLast();
    NativeByteBuffer * getFirst();
    void discard(uint32_t count);
    void clean();

    uint32_t getNextBufferSize();

private:
    std::vector<NativeByteBuffer *> buffersQueue;

};

#endif