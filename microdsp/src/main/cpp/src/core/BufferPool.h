#ifndef BUFFER_POOL_H
#define BUFFER_POOL_H

#include "Types.h"
#include "RingBuffer.h"
#include <memory>
#include <vector>
#include <unordered_map>
#include <mutex>
#include <thread>
#include <condition_variable>
#include <chrono>
#include <android/log.h>

#define LOG_TAG "BufferPool"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * @class BufferPool
 * @brief Memory management class for recycling RingBuffers.
 *
 * BufferPool maintains a pool of pre-allocated RingBuffer objects of various types
 * and capacities. This avoids costly heap allocations and deallocations in the
 * high-speed DSP processing path.
 */
class BufferPool {
public:
    /**
     * @struct BufferConfig
     * @brief Configuration for a specific type of buffer in the pool
     */
    struct BufferConfig {
        DataType type;         /**< Data type for the buffers */
        size_t capacity;       /**< Item capacity of the buffers */
        size_t prealloc_count; /**< Number of buffers to pre-allocate */
    };

    /**
     * @brief Get the singleton instance of BufferPool
     * @return Reference to the BufferPool instance
     */
    static BufferPool& get_instance();

    /**
     * @brief Acquire a buffer from the pool or create a new one
     * @param type Desired data type
     * @param capacity Desired item capacity
     * @return A unique pointer to an acquired RingBuffer
     */
    std::unique_ptr<RingBuffer> acquire_buffer(DataType type, size_t capacity);

    /**
     * @brief Return a buffer to the pool for later reuse
     * @param buffer The buffer to release
     */
    void release_buffer(std::unique_ptr<RingBuffer> buffer);

    /**
     * Configure the pool for a specific buffer type
     * @param config Buffer configuration
     */
    void configure_pool(const BufferConfig& config);

    /**
     * Set maximum number of buffers in the pool
     * @param max_buffers Maximum buffer count
     */
    void set_max_buffers(size_t max_buffers);

    /**
     * Set cleanup interval for the background worker
     * @param interval Cleanup interval
     */
    void set_cleanup_interval(std::chrono::milliseconds interval);

    /**
     * Get number of available buffers of a specific type
     * @param type Data type
     * @param capacity Buffer capacity
     * @return Number of available buffers
     */
    size_t get_available_count(DataType type, size_t capacity) const;

    /**
     * Get total number of allocated buffers
     */
    size_t get_total_allocated() const;

    /**
     * Get peak buffer usage
     */
    size_t get_peak_usage() const;

    /**
     * Get number of pools
     */
    size_t get_pool_count() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return pools_.size();
    }

    /**
     * Force cleanup of unused buffers
     */
    void cleanup();

    /**
     * Shrink memory usage to fit current needs
     */
    void shrink_to_fit();

private:
    BufferPool();
    ~BufferPool();

    // Non-copyable
    BufferPool(const BufferPool&) = delete;
    BufferPool& operator=(const BufferPool&) = delete;

    struct BufferKey {
        DataType type;
        size_t capacity;

        bool operator==(const BufferKey& other) const {
            return type == other.type && capacity == other.capacity;
        }
    };

    struct BufferKeyHash {
        std::size_t operator()(const BufferKey& k) const {
            return std::hash<int>{}(static_cast<int>(k.type)) ^
                   (std::hash<size_t>{}(k.capacity) << 1);
        }
    };

    using BufferList = std::vector<std::unique_ptr<RingBuffer>>;

    /**
     * Background cleanup worker thread function
     */
    void cleanup_worker();

    std::unordered_map<BufferKey, BufferList, BufferKeyHash> pools_;
    mutable std::mutex mutex_;
    std::condition_variable cv_;

    size_t max_buffers_{1000};
    size_t total_allocated_{0};
    size_t peak_usage_{0};
    std::chrono::milliseconds cleanup_interval_{5000};

    // Background cleanup thread
    std::atomic<bool> cleanup_running_{false};
    std::unique_ptr<std::thread> cleanup_thread_;
};

#endif // BUFFER_POOL_H