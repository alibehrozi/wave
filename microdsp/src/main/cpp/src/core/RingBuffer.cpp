#include "RingBuffer.h"
#include <android/log.h>

#define LOG_TAG "RingBuffer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Most of RingBuffer is template-based and implemented in the header.
// This file contains any non-template implementations that need to be
// separated from the header to avoid multiple definition issues.

// Note: All template methods are defined in the header, so this file
// is primarily for documentation and future expansion.

// If we need to add runtime type checking or conversion functions,
// they would go here.
