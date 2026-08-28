#include "Types.h"
#include <android/log.h>

#define LOG_TAG "Types"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/**
 * Get the size of a DataType in bytes (runtime version)
 */
size_t data_type_size(DataType type) {
    return get_type_size(type);
}

/**
 * Check if a DataType is numeric (supports arithmetic)
 */
bool is_numeric_type(DataType type) {
    return supports_arithmetic(type);
}

/**
 * Check if a DataType is a complex type
 */
bool is_complex_data_type(DataType type) {
    return is_complex_type(type);
}

/**
 * Check if two DataTypes are compatible
 */
bool are_data_types_compatible(DataType type1, DataType type2) {
    return are_types_compatible(type1, type2);
}

/**
 * Log DataType information (debugging)
 */
void log_data_type_info(DataType type, const char* context) {
    LOGI("DataType info%s: type=%s, size=%zu, arithmetic=%d, complex=%d",
         context ? context : "",
         data_type_to_string(type),
         get_type_size(type),
         supports_arithmetic(type),
         is_complex_type(type));
}