#ifndef BYTEARRAY_H
#define BYTEARRAY_H

#include <stdint.h>

class ByteArray {

public:
    ByteArray();
    ByteArray(uint32_t len);
    ByteArray(ByteArray *byteArray);
    ByteArray(uint8_t *buffer, uint32_t len);
    ~ByteArray();
    void alloc(uint32_t len);

    uint32_t length;
    uint8_t *bytes;

    bool isEqualTo(ByteArray *byteArray);

};

#endif