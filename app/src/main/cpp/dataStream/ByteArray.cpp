#include <cstdlib>
#include <memory.h>
#include "ByteArray.h"
#include "SimpleLog.h"

ByteArray::ByteArray() {
    bytes = nullptr;
    length = 0;
}

ByteArray::ByteArray(uint32_t len) {
    bytes = new uint8_t[len];
    if (bytes == nullptr) {
        LOGE("unable to allocate byte buffer %u", len);
        exit(1);
    }
    length = len;
}

ByteArray::ByteArray(ByteArray *byteArray) {
    bytes = new uint8_t[byteArray->length];
    if (bytes == nullptr) {
        LOGE("unable to allocate byte buffer %u", byteArray->length);
        exit(1);
    }
    length = byteArray->length;
    memcpy(bytes, byteArray->bytes, length);
}

ByteArray::ByteArray(uint8_t *buffer, uint32_t len) {
    bytes = new uint8_t[len];
    if (bytes == nullptr) {
        LOGE("unable to allocate byte buffer %u", len);
        exit(1);
    }
    length = len;
    memcpy(bytes, buffer, length);
}

ByteArray::~ByteArray() {
    if (bytes != nullptr) {
        delete[] bytes;
        bytes = nullptr;
    }
}

void ByteArray::alloc(uint32_t len) {
    if (bytes != nullptr) {
        delete[] bytes;
        bytes = nullptr;
    }
    bytes = new uint8_t[len];
    if (bytes == nullptr) {
        LOGE("unable to allocate byte buffer %u", len);
        exit(1);
    }
    length = len;
}

bool ByteArray::isEqualTo(ByteArray *byteArray) {
    return byteArray->length == length && !memcmp(byteArray->bytes, bytes, length);
}