#ifndef TYPES_H
#define TYPES_H

#include <cstdint>
#include <complex>
#include <memory>
#include <vector>
#include <type_traits>
#include <string>
#include <iostream>
#include <algorithm>

/**
 * @enum DataType
 * @brief Supported signal data types in the DSP system.
 */
enum class DataType {
    BYTE,           /**< 8-bit unsigned integer */
    COMPLEX_FLOAT,  /**< std::complex<float> (IQ) */
    COMPLEX_DOUBLE, /**< std::complex<double> (High-precision IQ) */
    FLOAT,          /**< 32-bit floating point */
    DOUBLE,         /**< 64-bit floating point */
    INT32,          /**< 32-bit signed integer */
    SHORT           /**< 16-bit signed integer */
};

template<DataType T>
struct type_traits {};

template<> struct type_traits<DataType::BYTE> {
    using type = uint8_t;
    static constexpr bool supports_arithmetic = false;
    static constexpr bool supports_complex = false;
    static constexpr const char* name = "BYTE";
    static constexpr size_t size = sizeof(uint8_t);
};

template<> struct type_traits<DataType::COMPLEX_FLOAT> {
    using type = std::complex<float>;
    static constexpr bool supports_arithmetic = true;
    static constexpr bool supports_complex = true;
    static constexpr const char* name = "COMPLEX_FLOAT";
    static constexpr size_t size = sizeof(std::complex<float>);
};

template<> struct type_traits<DataType::COMPLEX_DOUBLE> {
    using type = std::complex<double>;
    static constexpr bool supports_arithmetic = true;
    static constexpr bool supports_complex = true;
    static constexpr const char* name = "COMPLEX_DOUBLE";
    static constexpr size_t size = sizeof(std::complex<double>);
};

template<> struct type_traits<DataType::FLOAT> {
    using type = float;
    static constexpr bool supports_arithmetic = true;
    static constexpr bool supports_complex = false;
    static constexpr const char* name = "FLOAT";
    static constexpr size_t size = sizeof(float);
};

template<> struct type_traits<DataType::DOUBLE> {
    using type = double;
    static constexpr bool supports_arithmetic = true;
    static constexpr bool supports_complex = false;
    static constexpr const char* name = "DOUBLE";
    static constexpr size_t size = sizeof(double);
};

template<> struct type_traits<DataType::INT32> {
    using type = int32_t;
    static constexpr bool supports_arithmetic = true;
    static constexpr bool supports_complex = false;
    static constexpr const char* name = "INT32";
    static constexpr size_t size = sizeof(int32_t);
};

template<> struct type_traits<DataType::SHORT> {
    using type = int16_t;
    static constexpr bool supports_arithmetic = true;
    static constexpr bool supports_complex = false;
    static constexpr const char* name = "SHORT";
    static constexpr size_t size = sizeof(int16_t);
};

/**
 * Get the size in bytes of a data type
 */
constexpr size_t get_type_size(DataType type) {
    switch (type) {
        case DataType::BYTE: return sizeof(uint8_t);
        case DataType::COMPLEX_FLOAT: return sizeof(std::complex<float>);
        case DataType::COMPLEX_DOUBLE: return sizeof(std::complex<double>);
        case DataType::FLOAT: return sizeof(float);
        case DataType::DOUBLE: return sizeof(double);
        case DataType::INT32: return sizeof(int32_t);
        case DataType::SHORT: return sizeof(int16_t);
        default: return 0;
    }
}

/**
 * Check if a type supports arithmetic operations
 */
constexpr bool supports_arithmetic(DataType type) {
    switch (type) {
        case DataType::COMPLEX_FLOAT:
        case DataType::COMPLEX_DOUBLE:
        case DataType::FLOAT:
        case DataType::DOUBLE:
        case DataType::INT32:
        case DataType::SHORT:
            return true;
        case DataType::BYTE:
        default:
            return false;
    }
}

/**
 * Check if a type is complex (has real and imaginary parts)
 */
constexpr bool is_complex_type(DataType type) {
    return type == DataType::COMPLEX_FLOAT || type == DataType::COMPLEX_DOUBLE;
}

/**
 * Check if a type is floating point
 */
constexpr bool is_floating_point_type(DataType type) {
    return type == DataType::FLOAT || type == DataType::DOUBLE ||
           type == DataType::COMPLEX_FLOAT || type == DataType::COMPLEX_DOUBLE;
}

/**
 * Check if a type is integer
 */
constexpr bool is_integer_type(DataType type) {
    return type == DataType::BYTE || type == DataType::INT32 || type == DataType::SHORT;
}

/**
 * @brief Get the DataType enum value corresponding to a C++ type
 * @tparam T The C++ type
 * @return The corresponding DataType enum
 */
template<typename T>
constexpr DataType type_to_enum();

template<> constexpr DataType type_to_enum<uint8_t>() { return DataType::BYTE; }
template<> constexpr DataType type_to_enum<std::complex<float>>() { return DataType::COMPLEX_FLOAT; }
template<> constexpr DataType type_to_enum<std::complex<double>>() { return DataType::COMPLEX_DOUBLE; }
template<> constexpr DataType type_to_enum<float>() { return DataType::FLOAT; }
template<> constexpr DataType type_to_enum<double>() { return DataType::DOUBLE; }
template<> constexpr DataType type_to_enum<int32_t>() { return DataType::INT32; }
template<> constexpr DataType type_to_enum<int16_t>() { return DataType::SHORT; }

/**
 * Convert DataType to string (runtime version)
 */
inline const char* data_type_to_string(DataType type) {
    switch (type) {
        case DataType::BYTE: return "BYTE";
        case DataType::COMPLEX_FLOAT: return "COMPLEX_FLOAT";
        case DataType::COMPLEX_DOUBLE: return "COMPLEX_DOUBLE";
        case DataType::FLOAT: return "FLOAT";
        case DataType::DOUBLE: return "DOUBLE";
        case DataType::INT32: return "INT32";
        case DataType::SHORT: return "SHORT";
        default: return "UNKNOWN";
    }
}

/**
 * Parse DataType from string
 */
inline DataType string_to_data_type(const std::string& str) {
    if (str == "BYTE") return DataType::BYTE;
    if (str == "COMPLEX_FLOAT") return DataType::COMPLEX_FLOAT;
    if (str == "COMPLEX_DOUBLE") return DataType::COMPLEX_DOUBLE;
    if (str == "FLOAT") return DataType::FLOAT;
    if (str == "DOUBLE") return DataType::DOUBLE;
    if (str == "INT32") return DataType::INT32;
    if (str == "SHORT") return DataType::SHORT;
    return DataType::BYTE; // Default fallback
}

/**
 * Check if two data types are compatible for data transfer
 */
inline bool are_types_compatible(DataType type1, DataType type2) {
    if (type1 == type2) return true;

    // Float and double are compatible
    if ((type1 == DataType::FLOAT || type1 == DataType::DOUBLE) &&
        (type2 == DataType::FLOAT || type2 == DataType::DOUBLE)) {
        return true;
    }

    // Integer types are compatible
    if ((type1 == DataType::INT32 || type1 == DataType::SHORT || type1 == DataType::BYTE) &&
        (type2 == DataType::INT32 || type2 == DataType::SHORT || type2 == DataType::BYTE)) {
        return true;
    }

    // Complex types only compatible with same type
    if (type1 == DataType::COMPLEX_FLOAT && type2 == DataType::COMPLEX_FLOAT) {
        return true;
    }
    if (type1 == DataType::COMPLEX_DOUBLE && type2 == DataType::COMPLEX_DOUBLE) {
        return true;
    }

    // Complex to non-complex is not allowed
    if (is_complex_type(type1) || is_complex_type(type2)) {
        return false;
    }

    return false;
}

using gr_complex = std::complex<float>;
using gr_complex_d = std::complex<double>;

/**
 * Template to get the C++ type from DataType at runtime
 * Note: This requires runtime type checking and is not compile-time safe
 */
template<typename T>
inline bool is_type(DataType type) {
    return type_to_enum<T>() == type;
}

/**
 * Safely cast data from one type to another (if compatible)
 * This is a runtime operation and should be used with caution
 */
template<typename T, typename U>
inline T safe_cast(U value, DataType target_type) {
    // This is a placeholder - actual implementation depends on use case
    return static_cast<T>(value);
}

#endif // TYPES_H