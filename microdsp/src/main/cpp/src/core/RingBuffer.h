#ifndef RING_BUFFER_H
#define RING_BUFFER_H

#include "Types.h"
#include <atomic>
#include <vector>
#include <memory>
#include <cstring>
#include <stdexcept>
#include <algorithm>
#include <android/log.h>

// Logging macros used by RingBuffer's template methods.
// Each is individually guarded to avoid overriding macros defined in including TUs.
#ifndef LOGE
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "RingBuffer", __VA_ARGS__)
#endif
#ifndef LOGI
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "RingBuffer", __VA_ARGS__)
#endif
#ifndef LOGV
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, "RingBuffer", __VA_ARGS__)
#endif


/**
 * @class RingBuffer
 * @brief A thread-safe, lock-free SPSC (Single-Producer Single-Consumer) ring buffer.
 *
 * Optimized for high-throughput DSP data transfer. Uses power-of-two capacity
 * to replace modulo with bitwise AND. Supports type-safe read/write operations.
 */
class RingBuffer {
public:
    /**
     * @brief Create a new RingBuffer
     * @param capacity Minimum number of items (will be rounded up to power of two)
     * @param type The data type of items stored in this buffer
     * @throws std::invalid_argument if capacity is 0
     */
    RingBuffer(size_t capacity, DataType type)
            : capacity_(capacity), type_(type), item_size_(get_type_size(type)) {

        if (capacity == 0) {
            throw std::invalid_argument("Capacity must be greater than 0");
        }

        // Ensure capacity is power of two for efficient modulo
        capacity_ = next_power_of_two(capacity);
        buffer_.resize(capacity_ * item_size_);

        read_index_.store(0, std::memory_order_relaxed);
        write_index_.store(0, std::memory_order_relaxed);

        LOGI("RingBuffer created: capacity=%zu, type=%d, item_size=%zu, total_bytes=%zu",
             capacity_, static_cast<int>(type_), item_size_, buffer_.size());
    }

    // Non-copyable, but movable
    RingBuffer(const RingBuffer&) = delete;
    RingBuffer& operator=(const RingBuffer&) = delete;

    RingBuffer(RingBuffer&& other) noexcept
            : buffer_(std::move(other.buffer_)),
              capacity_(other.capacity_),
              type_(other.type_),
              item_size_(other.item_size_),
              read_index_(other.read_index_.load()),
              write_index_(other.write_index_.load()) {}

    RingBuffer& operator=(RingBuffer&& other) noexcept {
        if (this != &other) {
            buffer_ = std::move(other.buffer_);
            capacity_ = other.capacity_;
            type_ = other.type_;
            item_size_ = other.item_size_;
            read_index_.store(other.read_index_.load());
            write_index_.store(other.write_index_.load());
        }
        return *this;
    }

    /**
     * Get number of items available to write
     * @return Number of items that can be written
     */
    size_t write_available() const {
        const size_t write_idx = write_index_.load(std::memory_order_acquire);
        const size_t read_idx = read_index_.load(std::memory_order_acquire);
        const size_t occupied = write_idx - read_idx;
        return (occupied < capacity_) ? (capacity_ - 1 - occupied) : 0;
    }

    /**
     * Get number of items available to read
     * @return Number of items that can be read
     */
    size_t read_available() const {
        const size_t write_idx = write_index_.load(std::memory_order_acquire);
        const size_t read_idx = read_index_.load(std::memory_order_acquire);
        return write_idx - read_idx;
    }

    /**
     * Check if there is enough space to write specific number of items
     * @param count Number of items
     * @return true if enough space
     */
    bool has_space(size_t count) const {
        return write_available() >= count;
    }

    /**
     * Check if there is enough data to read specific number of items
     * @param count Number of items
     * @return true if enough data
     */
    bool has_data(size_t count) const {
        return read_available() >= count;
    }

    /**
     * Get total capacity of the buffer
     * @return Buffer capacity in items
     */
    size_t get_capacity() const { return capacity_; }

    /**
     * Get the data type of items in the buffer
     * @return Data type
     */
    DataType get_type() const { return type_; }

    /**
     * Get the item size in bytes
     * @return Item size
     */
    size_t get_item_size() const { return item_size_; }

    /**
     * Check if the buffer is empty
     * @return true if empty
     */
    bool is_empty() const {
        return read_available() == 0;
    }

    /**
     * Check if the buffer is full
     * @return true if full
     */
    bool is_full() const {
        return write_available() == 0;
    }

    /**
     * Clear all data from the buffer
     */
    void clear() {
        read_index_.store(write_index_.load(std::memory_order_acquire),
                          std::memory_order_release);
    }

    /**
     * Reset the buffer to initial state (discard all data)
     */
    void reset() {
        read_index_.store(0, std::memory_order_release);
        write_index_.store(0, std::memory_order_release);
    }

    /**
     * Write data to the buffer (type-safe)
     * @param data Pointer to data to write
     * @param count Number of items to write
     * @return true if successful, false if not enough space
     * @throws std::invalid_argument if type mismatch
     */
    template<typename T>
    bool write(const T* data, size_t count) {
        static_assert(!std::is_same_v<T, void>, "Cannot write void type");

        if (!data) {
            LOGE("RingBuffer::write - data is null");
            return false;
        }

        if (count == 0) {
            return true;
        }

        if (type_to_enum<T>() != type_) {
            LOGE("RingBuffer::write - Type mismatch: expected %d, got %d",
                 static_cast<int>(type_), static_cast<int>(type_to_enum<T>()));
            throw std::invalid_argument("Data type mismatch");
        }

        // Calculate bytes to write
        size_t bytes = count * sizeof(T);

        // Check if we have enough space
        size_t available = write_available();
        if (count > available) {
            LOGV("RingBuffer::write - Not enough space: need %zu, available %zu",
                 count, available);
            return false;
        }

        return write_impl(data, bytes);
    }

    /**
     * Write data from a vector (type-safe)
     * @param data Vector of data to write
     * @return true if successful
     */
    template<typename T>
    bool write(const std::vector<T>& data) {
        return write(data.data(), data.size());
    }

    /**
     * Read data from the buffer (type-safe)
     * @param data Pointer to buffer to read into
     * @param count Number of items to read
     * @return true if successful, false if not enough data
     * @throws std::invalid_argument if type mismatch
     */
    template<typename T>
    bool read(T* data, size_t count) {
        static_assert(!std::is_same_v<T, void>, "Cannot read void type");

        if (!data) {
            LOGE("RingBuffer::read - data is null");
            return false;
        }

        if (count == 0) {
            return true;
        }

        if (type_to_enum<T>() != type_) {
            LOGE("RingBuffer::read - Type mismatch: expected %d, got %d",
                 static_cast<int>(type_), static_cast<int>(type_to_enum<T>()));
            throw std::invalid_argument("Data type mismatch");
        }

        // Calculate bytes to read
        size_t bytes = count * sizeof(T);

        // Check if we have enough data
        size_t available = read_available();
        if (count > available) {
            LOGV("RingBuffer::read - Not enough data: need %zu, available %zu",
                 count, available);
            return false;
        }

        return read_impl(data, bytes);
    }

    /**
     * Read data into a vector (type-safe)
     * @param data Vector to read into (will be resized)
     * @param count Number of items to read
     * @return true if successful
     */
    template<typename T>
    bool read(std::vector<T>& data, size_t count) {
        data.resize(count);
        return read(data.data(), count);
    }

    /**
     * Read all available data into a vector
     * @param data Vector to read into
     * @return Number of items read
     */
    template<typename T>
    size_t read_all(std::vector<T>& data) {
        size_t available = read_available();
        if (available == 0) {
            data.clear();
            return 0;
        }
        data.resize(available);
        return read(data.data(), available) ? available : 0;
    }

    /**
     * Peek at data in the buffer without consuming it
     * @param data Pointer to buffer to read into
     * @param count Number of items to peek
     * @return true if successful
     */
    template<typename T>
    bool peek(T* data, size_t count) const {
        static_assert(!std::is_same_v<T, void>, "Cannot peek void type");

        if (!data) {
            LOGE("RingBuffer::peek - data is null");
            return false;
        }

        if (count == 0) {
            return true;
        }

        if (type_to_enum<T>() != type_) {
            LOGE("RingBuffer::peek - Type mismatch: expected %d, got %d",
                 static_cast<int>(type_), static_cast<int>(type_to_enum<T>()));
            throw std::invalid_argument("Data type mismatch");
        }

        size_t bytes = count * sizeof(T);
        size_t available = read_available();
        if (count > available) {
            LOGV("RingBuffer::peek - Not enough data: need %zu, available %zu",
                 count, available);
            return false;
        }

        return peek_impl(data, bytes);
    }

    /**
     * Peek all available data into a vector without consuming
     * @param data Vector to read into
     * @return Number of items peeked
     */
    template<typename T>
    size_t peek_all(std::vector<T>& data) const {
        size_t available = read_available();
        if (available == 0) {
            data.clear();
            return 0;
        }
        data.resize(available);
        return peek(data.data(), available) ? available : 0;
    }

    /**
     * Skip data without reading (consume without copying)
     * @param count Number of items to skip
     * @return true if successful
     */
    bool skip(size_t count) {
        size_t available = read_available();
        if (count > available) {
            return false;
        }

        size_t read_idx = read_index_.load(std::memory_order_acquire);
        read_index_.store(read_idx + count, std::memory_order_release);
        return true;
    }

    /**
     * @brief Raw binary write (no type checking)
     * @param data Pointer to raw data
     * @param count Number of items (NOT bytes)
     */
    bool write_raw(const void* data, size_t count) {
        if (!data) return false;
        if (count == 0) return true;
        return write_impl(data, count * item_size_);
    }

    /**
     * @brief Raw binary read (no type checking)
     * @param data Pointer to destination buffer
     * @param count Number of items (NOT bytes)
     */
    bool read_raw(void* data, size_t count) {
        if (!data || count == 0) return true;
        return read_impl(data, count * item_size_);
    }

    /**
     * Get the number of items that can be written without blocking
     * @return Available write space
     */
    size_t space() const { return write_available(); }

    /**
     * Get the number of items available to read
     * @return Available read data
     */
    size_t size() const { return read_available(); }

    /**
     * Get the current read position (for debugging)
     */
    size_t get_read_index() const {
        return read_index_.load(std::memory_order_acquire);
    }

    /**
     * Get the current write position (for debugging)
     */
    size_t get_write_index() const {
        return write_index_.load(std::memory_order_acquire);
    }

private:
    std::vector<uint8_t> buffer_;
    size_t capacity_;
    DataType type_;
    size_t item_size_;

    mutable std::atomic<size_t> read_index_;
    mutable std::atomic<size_t> write_index_;

    /**
     * Round up to next power of two
     */
    static size_t next_power_of_two(size_t n) {
        if (n == 0) return 1;
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        // Only shift by 32 on 64-bit platforms; shifting a 32-bit type by 32 is UB in C++
        if constexpr (sizeof(size_t) > 4) {
            n |= n >> 32;
        }
        return n + 1;
    }

    /**
     * Implementation of write operation
     */
    bool write_impl(const void* data, size_t bytes) {
        const size_t write_idx = write_index_.load(std::memory_order_acquire);
        const size_t read_idx = read_index_.load(std::memory_order_acquire);

        // Calculate required items
        const size_t required_items = (bytes + item_size_ - 1) / item_size_;

        // Check available space (leave one slot empty to distinguish full/empty)
        const size_t occupied = write_idx - read_idx;
        const size_t available_space = (occupied < capacity_) ? (capacity_ - 1 - occupied) : 0;
        if (required_items > available_space) {
            return false;
        }

        const size_t mask = capacity_ - 1;
        size_t current_index = write_idx & mask;
        size_t to_end = capacity_ - current_index;

        const uint8_t* src = static_cast<const uint8_t*>(data);

        if (required_items <= to_end) {
            // Single contiguous write
            std::memcpy(&buffer_[current_index * item_size_], src, bytes);
        } else {
            // Wrap-around write
            size_t first_chunk_bytes = to_end * item_size_;
            std::memcpy(&buffer_[current_index * item_size_], src, first_chunk_bytes);
            std::memcpy(&buffer_[0], src + first_chunk_bytes, bytes - first_chunk_bytes);
        }

        // Update write index atomically
        write_index_.store(write_idx + required_items, std::memory_order_release);

        return true;
    }

    /**
     * Implementation of read operation
     */
    bool read_impl(void* data, size_t bytes) {
        const size_t write_idx = write_index_.load(std::memory_order_acquire);
        const size_t read_idx = read_index_.load(std::memory_order_acquire);

        const size_t available = write_idx - read_idx;
        const size_t required_items = (bytes + item_size_ - 1) / item_size_;

        if (required_items > available) {
            return false;
        }

        const size_t mask = capacity_ - 1;
        size_t current_index = read_idx & mask;
        size_t to_end = capacity_ - current_index;

        uint8_t* dst = static_cast<uint8_t*>(data);

        if (required_items <= to_end) {
            std::memcpy(dst, &buffer_[current_index * item_size_], bytes);
        } else {
            size_t first_chunk = to_end * item_size_;
            std::memcpy(dst, &buffer_[current_index * item_size_], first_chunk);
            std::memcpy(dst + first_chunk, &buffer_[0], bytes - first_chunk);
        }

        read_index_.store(read_idx + required_items, std::memory_order_release);
        return true;
    }

    /**
     * Implementation of peek operation
     */
    bool peek_impl(void* data, size_t bytes) const {
        const size_t write_idx = write_index_.load(std::memory_order_acquire);
        const size_t read_idx = read_index_.load(std::memory_order_acquire);

        const size_t available = write_idx - read_idx;
        const size_t required_items = (bytes + item_size_ - 1) / item_size_;

        if (required_items > available) {
            return false;
        }

        const size_t mask = capacity_ - 1;
        size_t current_index = read_idx & mask;
        size_t to_end = capacity_ - current_index;

        uint8_t* dst = static_cast<uint8_t*>(data);

        if (required_items <= to_end) {
            std::memcpy(dst, &buffer_[current_index * item_size_], bytes);
        } else {
            size_t first_chunk = to_end * item_size_;
            std::memcpy(dst, &buffer_[current_index * item_size_], first_chunk);
            std::memcpy(dst + first_chunk, &buffer_[0], bytes - first_chunk);
        }

        return true;
    }
};

#endif // RING_BUFFER_H