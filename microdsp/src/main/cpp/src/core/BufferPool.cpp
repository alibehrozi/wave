#include "BufferPool.h"
#include <algorithm>
#include <thread>
#include <chrono>
#include <android/log.h>

#define LOG_TAG "BufferPool"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

BufferPool::BufferPool() {
    cleanup_running_.store(true, std::memory_order_release);
    cleanup_thread_ = std::make_unique<std::thread>(&BufferPool::cleanup_worker, this);
    LOGI("BufferPool created with cleanup thread");
}

BufferPool::~BufferPool() {
    LOGI("BufferPool destroying...");
    cleanup_running_.store(false, std::memory_order_release);

    if (cleanup_thread_ && cleanup_thread_->joinable()) {
        cleanup_thread_->join();
    }

    LOGI("BufferPool destroyed. Final stats - Total allocated: %zu, Peak usage: %zu",
         total_allocated_, peak_usage_);
}

BufferPool& BufferPool::get_instance() {
    static BufferPool instance;
    return instance;
}

std::unique_ptr<RingBuffer> BufferPool::acquire_buffer(DataType type, size_t capacity) {
    std::unique_lock<std::mutex> lock(mutex_);

    BufferKey key{type, capacity};
    auto& pool = pools_[key];

    // Try to get a buffer from the pool
    if (!pool.empty()) {
        auto buffer = std::move(pool.back());
        pool.pop_back();

        // Clear the buffer before reuse
        if (buffer) {
            buffer->clear();
        }

        LOGV("Acquired buffer from pool: type=%d, capacity=%zu, available=%zu",
             static_cast<int>(type), capacity, pool.size());

        return buffer;
    }

    // No buffer available, create a new one
    lock.unlock();

    auto buffer = std::make_unique<RingBuffer>(capacity, type);

    {
        std::lock_guard<std::mutex> lock_guard(mutex_);
        total_allocated_++;
        peak_usage_ = std::max(peak_usage_, total_allocated_);

        LOGI("Created new buffer: type=%d, capacity=%zu, total_allocated=%zu, peak=%zu",
             static_cast<int>(type), capacity, total_allocated_, peak_usage_);
    }

    return buffer;
}

void BufferPool::release_buffer(std::unique_ptr<RingBuffer> buffer) {
    if (!buffer) {
        LOGE("Cannot release null buffer");
        return;
    }

    std::lock_guard<std::mutex> lock(mutex_);

    DataType type = buffer->get_type();
    size_t capacity = buffer->get_capacity();

    BufferKey key{type, capacity};
    auto& pool = pools_[key];

    // Check if we should keep this buffer or discard it
    size_t pool_size = pool.size();
    size_t max_per_pool = max_buffers_ / std::max(static_cast<size_t>(1UL), pools_.size());

    if (pool_size < max_per_pool) {
        // Reset the buffer before returning to pool
        buffer->clear();
        pool.push_back(std::move(buffer));

        LOGV("Released buffer to pool: type=%d, capacity=%zu, pool_size=%zu",
             static_cast<int>(type), capacity, pool.size());
    } else {
        // Pool is full, let the buffer be destroyed
        if (total_allocated_ > 0) {
            total_allocated_--;
        }
        LOGV("Buffer discarded (pool full): type=%d, capacity=%zu, total_allocated=%zu",
             static_cast<int>(type), capacity, total_allocated_);
    }

    cv_.notify_one();
}

void BufferPool::configure_pool(const BufferConfig& config) {
    std::lock_guard<std::mutex> lock(mutex_);

    BufferKey key{config.type, config.capacity};
    auto& pool = pools_[key];

    // Preallocate buffers
    size_t preallocated = 0;
    for (size_t i = 0; i < config.prealloc_count && total_allocated_ < max_buffers_; ++i) {
        auto buffer = std::make_unique<RingBuffer>(config.capacity, config.type);
        pool.push_back(std::move(buffer));
        total_allocated_++;
        preallocated++;
    }

    peak_usage_ = std::max(peak_usage_, total_allocated_);

    LOGI("Configured pool: type=%d, capacity=%zu, preallocated=%zu, total_allocated=%zu",
         static_cast<int>(config.type), config.capacity, preallocated, total_allocated_);
}

void BufferPool::set_max_buffers(size_t max_buffers) {
    std::lock_guard<std::mutex> lock(mutex_);
    max_buffers_ = max_buffers;
    LOGI("Max buffers set to %zu", max_buffers);
}

void BufferPool::set_cleanup_interval(std::chrono::milliseconds interval) {
    cleanup_interval_ = interval;
    LOGI("Cleanup interval set to %lld ms", interval.count());
}

size_t BufferPool::get_available_count(DataType type, size_t capacity) const {
    std::lock_guard<std::mutex> lock(mutex_);

    BufferKey key{type, capacity};
    auto it = pools_.find(key);
    return it != pools_.end() ? it->second.size() : 0;
}

size_t BufferPool::get_total_allocated() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return total_allocated_;
}

size_t BufferPool::get_peak_usage() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return peak_usage_;
}

void BufferPool::cleanup() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (pools_.empty()) {
        return;
    }

    // Calculate target size per pool
    size_t total_pools = pools_.size();
    size_t target_per_pool = std::max(static_cast<size_t>(1UL), max_buffers_ / (2 * total_pools));

    size_t removed = 0;
    for (auto& [key, pool] : pools_) {
        if (pool.size() > target_per_pool) {
            size_t to_remove = pool.size() - target_per_pool;
            pool.resize(target_per_pool);
            removed += to_remove;
        }
    }

    if (removed > 0) {
        if (total_allocated_ >= removed) {
            total_allocated_ -= removed;
        } else {
            total_allocated_ = 0;
        }

        LOGV("Cleanup removed %zu buffers, total_allocated=%zu", removed, total_allocated_);
    }
}

void BufferPool::shrink_to_fit() {
    std::lock_guard<std::mutex> lock(mutex_);

    for (auto& [key, pool] : pools_) {
        pool.shrink_to_fit();
    }

    LOGI("All pools shrunk to fit");
}

void BufferPool::cleanup_worker() {
    LOGI("Cleanup worker started");

    while (cleanup_running_.load(std::memory_order_acquire)) {
        std::this_thread::sleep_for(cleanup_interval_);

        if (!cleanup_running_.load(std::memory_order_acquire)) {
            break;
        }

        try {
            cleanup();
        } catch (const std::exception& e) {
            LOGE("Error in cleanup worker: %s", e.what());
        } catch (...) {
            LOGE("Unknown error in cleanup worker");
        }
    }

    LOGI("Cleanup worker stopped");
}