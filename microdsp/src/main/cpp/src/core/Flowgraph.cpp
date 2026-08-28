#include "Flowgraph.h"
#include <iostream>
#include <chrono>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "Flowgraph"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

Flowgraph::Flowgraph(const std::string& name)
        : name_(name) {
}

Flowgraph::~Flowgraph() {
    stop();
}

bool Flowgraph::connect(const std::shared_ptr<Block>& src_block, const std::string& src_port,
                       const std::shared_ptr<Block>& dst_block, const std::string& dst_port) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!src_block || !dst_block) {
        LOGE("Cannot connect null blocks");
        return false;
    }

    Port* src_port_ptr = src_block->get_output_port(src_port);
    Port* dst_port_ptr = dst_block->get_input_port(dst_port);

    if (!src_port_ptr) {
        LOGE("Source block '%s' has no output port '%s'",
             src_block->get_name().c_str(), src_port.c_str());
        return false;
    }

    if (!dst_port_ptr) {
        LOGE("Destination block '%s' has no input port '%s'",
             dst_block->get_name().c_str(), dst_port.c_str());
        return false;
    }

    // Add blocks to internal management
    add_block_internal(src_block);
    add_block_internal(dst_block);

    // Create the connection
    if (src_port_ptr->connect(dst_port_ptr)) {
        Connection conn;
        conn.src_block = src_block;
        conn.src_port = src_port;
        conn.dst_block = dst_block;
        conn.dst_port = dst_port;
        connections_.push_back(conn);

        LOGI("Connected: %s:%s -> %s:%s",
             src_block->get_name().c_str(), src_port.c_str(),
             dst_block->get_name().c_str(), dst_port.c_str());
        return true;
    } else {
        LOGE("Failed to connect %s:%s -> %s:%s",
             src_block->get_name().c_str(), src_port.c_str(),
             dst_block->get_name().c_str(), dst_port.c_str());
        return false;
    }
}

bool Flowgraph::connect(const std::shared_ptr<Block>& src_block, size_t src_port_index,
                       const std::shared_ptr<Block>& dst_block, size_t dst_port_index) {
    if (!src_block || !dst_block) return false;

    Port* src_port_ptr = src_block->get_output_port(src_port_index);
    Port* dst_port_ptr = dst_block->get_input_port(dst_port_index);

    if (!src_port_ptr || !dst_port_ptr) return false;

    return connect(src_block, src_port_ptr->get_name(), dst_block, dst_port_ptr->get_name());
}

bool Flowgraph::connect(const std::shared_ptr<Block>& src_block,
                       const std::shared_ptr<Block>& dst_block) {
    if (!src_block || !dst_block) return false;

    if (src_block->get_output_port_count() > 0 && dst_block->get_input_port_count() > 0) {
        return connect(src_block, 0, dst_block, 0);
    }

    LOGE("Blocks don't have compatible ports for default connection");
    return false;
}

void Flowgraph::disconnect(const std::shared_ptr<Block>& src_block, const std::string& src_port,
                          const std::shared_ptr<Block>& dst_block, const std::string& dst_port) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!src_block || !dst_block) return;

    Port* src_port_ptr = src_block->get_output_port(src_port);
    if (src_port_ptr) {
        // Find and disconnect the specific connection
        for (auto* port : src_port_ptr->get_connections()) {
            if (port->get_parent() == dst_block.get() &&
                port->get_name() == dst_port) {
                src_port_ptr->disconnect(port);
                break;
            }
        }
    }

    // Remove from connections list
    connections_.erase(
            std::remove_if(connections_.begin(), connections_.end(),
                           [&](const Connection& conn) {
                               auto src = conn.src_block.lock();
                               auto dst = conn.dst_block.lock();
                               return src && dst &&
                                      src->get_name() == src_block->get_name() &&
                                      dst->get_name() == dst_block->get_name() &&
                                      conn.src_port == src_port &&
                                      conn.dst_port == dst_port;
                           }),
            connections_.end()
    );

    if (auto_cleanup_) {
        cleanup_disconnected_blocks();
    }
}

void Flowgraph::disconnect(const std::shared_ptr<Block>& src_block,
                          const std::shared_ptr<Block>& dst_block) {
    if (!src_block || !dst_block) return;

    std::vector<Connection> to_remove;

    {
        std::lock_guard<std::mutex> lock(mutex_);
        for (const auto& conn : connections_) {
            auto src = conn.src_block.lock();
            auto dst = conn.dst_block.lock();
            if (src && dst &&
                src->get_name() == src_block->get_name() &&
                dst->get_name() == dst_block->get_name()) {
                to_remove.push_back(conn);
            }
        }
    }

    for (const auto& conn : to_remove) {
        disconnect(src_block, conn.src_port, dst_block, conn.dst_port);
    }
}

void Flowgraph::remove_block(const std::shared_ptr<Block>& block) {
    if (!block) return;

    std::lock_guard<std::mutex> lock(mutex_);

    // Disconnect all connections involving this block.
    // We call port-level disconnect directly (not the Flowgraph::disconnect overload)
    // because this method already holds the mutex.
    std::vector<Connection> to_remove;
    for (const auto& conn : connections_) {
        auto src = conn.src_block.lock();
        auto dst = conn.dst_block.lock();
        if ((src && src->get_name() == block->get_name()) ||
            (dst && dst->get_name() == block->get_name())) {
            to_remove.push_back(conn);
        }
    }

    for (const auto& conn : to_remove) {
        auto src = conn.src_block.lock();
        auto dst = conn.dst_block.lock();
        if (src && dst) {
            // Disconnect at the port level directly to avoid re-locking the flowgraph mutex.
            Port* src_port_ptr = src->get_output_port(conn.src_port);
            Port* dst_port_ptr = dst->get_input_port(conn.dst_port);
            if (src_port_ptr && dst_port_ptr) {
                src_port_ptr->disconnect(dst_port_ptr);
            }
        }
    }

    // Remove tracked connections from the connections list
    connections_.erase(
        std::remove_if(connections_.begin(), connections_.end(),
            [&](const Connection& conn) {
                auto src = conn.src_block.lock();
                auto dst = conn.dst_block.lock();
                return (src && src->get_name() == block->get_name()) ||
                       (dst && dst->get_name() == block->get_name());
            }),
        connections_.end()
    );

    remove_block_internal(block);
}

void Flowgraph::add_block(const std::shared_ptr<Block>& block) {
    if (!block) return;
    std::lock_guard<std::mutex> lock(mutex_);
    add_block_internal(block);
}

void Flowgraph::remove_block(const std::string& name) {
    // Look up the block under the lock, then delegate to remove_block(ptr).
    // We cannot call remove_block(ptr) while holding the lock because that
    // method also acquires mutex_ — which would deadlock on a non-recursive mutex.
    std::shared_ptr<Block> block;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = block_map_.find(name);
        if (it != block_map_.end()) {
            block = it->second;
        }
    }
    if (block) {
        remove_block(block);
    }
}

std::shared_ptr<Block> Flowgraph::get_block(const std::string& name) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = block_map_.find(name);
    return it != block_map_.end() ? it->second : nullptr;
}

std::vector<std::shared_ptr<Block>> Flowgraph::get_blocks() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return blocks_;
}

size_t Flowgraph::get_connection_count() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return connections_.size();
}

bool Flowgraph::are_connected(const std::shared_ptr<Block>& src_block, const std::string& src_port,
                             const std::shared_ptr<Block>& dst_block, const std::string& dst_port) const {
    std::lock_guard<std::mutex> lock(mutex_);
    for (const auto& conn : connections_) {
        auto src = conn.src_block.lock();
        auto dst = conn.dst_block.lock();
        if (src && dst &&
            src->get_name() == src_block->get_name() &&
            dst->get_name() == dst_block->get_name() &&
            conn.src_port == src_port &&
            conn.dst_port == dst_port) {
            return true;
        }
    }
    return false;
}

void Flowgraph::start() {
    if (running_.exchange(true, std::memory_order_acq_rel)) {
        return;
    }

    stop_requested_.store(false, std::memory_order_release);

    std::lock_guard<std::mutex> lock(mutex_);

    for (auto& block : blocks_) {
        if (!block->start()) {
            LOGE("Failed to start block: %s", block->get_name().c_str());
            stop();
            return;
        }
    }

    start_time_ = std::chrono::steady_clock::now();
    iterations_ = 0;

    worker_thread_ = std::make_unique<std::thread>(&Flowgraph::worker_thread, this);
    LOGI("Flowgraph '%s' started with %zu blocks and %zu connections",
         name_.c_str(), blocks_.size(), connections_.size());
}

void Flowgraph::stop() {
    if (!running_.exchange(false, std::memory_order_acq_rel)) {
        return;
    }

    stop_requested_.store(true, std::memory_order_release);
    cv_.notify_all();

    if (worker_thread_ && worker_thread_->joinable()) {
        worker_thread_->join();
        worker_thread_.reset();
    }

    // Copy start_time_ under the lock to avoid a data race with start().
    std::chrono::steady_clock::time_point captured_start;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        captured_start = start_time_;
        for (auto& block : blocks_) {
            block->stop();
        }
    }

    auto end_time = std::chrono::steady_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - captured_start);
    LOGI("Flowgraph '%s' stopped. Ran for %lld ms, %zu iterations",
         name_.c_str(), duration.count(), iterations_);
}

void Flowgraph::wait() {
    if (worker_thread_ && worker_thread_->joinable()) {
        worker_thread_->join();
    }
}

void Flowgraph::run() {
    start();
    wait();
}

void Flowgraph::worker_thread() {
    constexpr size_t MAX_IDLE_SPINS = 4;
    size_t idle_iterations = 0;

    while (!stop_requested_.load(std::memory_order_acquire)) {
        bool work_done = false;

        // Snapshot active blocks under the lock, then release it before calling work().
        // Holding the mutex across work() would block add_block/remove_block/connect
        // for the entire duration of each block's processing.
        std::vector<std::shared_ptr<Block>> active_blocks;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            for (auto& block : blocks_) {
                if (block->is_active() && block->is_ready()) {
                    active_blocks.push_back(block);
                }
            }
        }

        for (auto& block : active_blocks) {
            if (stop_requested_.load(std::memory_order_acquire)) {
                break;
            }
            try {
                block->work();
                work_done = true;
            } catch (const std::exception& e) {
                LOGE("Error in block '%s': %s", block->get_name().c_str(), e.what());
            }
        }

        iterations_++;

        if (!work_done) {
            idle_iterations++;
            if (idle_iterations >= MAX_IDLE_SPINS) {
                std::unique_lock<std::mutex> lock(mutex_);
                cv_.wait_for(lock, std::chrono::microseconds(500));
                idle_iterations = 0;
            } else {
                std::this_thread::yield();
            }
        } else {
            idle_iterations = 0;
        }
    }
}

void Flowgraph::add_block_internal(const std::shared_ptr<Block>& block) {
    if (!block) return;

    const std::string& name = block->get_name();
    if (block_map_.find(name) == block_map_.end()) {
        blocks_.push_back(block);
        block_map_[name] = block;
        LOGI("Added block: %s", name.c_str());
    }
}

void Flowgraph::remove_block_internal(const std::shared_ptr<Block>& block) {
    if (!block) return;

    const std::string& name = block->get_name();
    auto it = block_map_.find(name);
    if (it != block_map_.end()) {
        block->stop();
        blocks_.erase(std::remove(blocks_.begin(), blocks_.end(), block), blocks_.end());
        block_map_.erase(it);
        LOGI("Removed block: %s", name.c_str());
    }
}

void Flowgraph::cleanup_disconnected_blocks() {
    std::set<std::shared_ptr<Block>> connected_blocks;

    for (const auto& conn : connections_) {
        auto src = conn.src_block.lock();
        auto dst = conn.dst_block.lock();
        if (src) connected_blocks.insert(src);
        if (dst) connected_blocks.insert(dst);
    }

    std::vector<std::shared_ptr<Block>> to_remove;
    for (const auto& block : blocks_) {
        if (connected_blocks.find(block) == connected_blocks.end()) {
            to_remove.push_back(block);
        }
    }

    for (const auto& block : to_remove) {
        remove_block_internal(block);
    }
}

bool Flowgraph::validate_connection(const std::shared_ptr<Block>& src_block, const std::string& src_port,
                                   const std::shared_ptr<Block>& dst_block, const std::string& dst_port) const {
    if (!src_block || !dst_block) return false;

    Port* src_port_ptr = src_block->get_output_port(src_port);
    Port* dst_port_ptr = dst_block->get_input_port(dst_port);

    if (!src_port_ptr || !dst_port_ptr) return false;

    return src_port_ptr->validate_connection(dst_port_ptr);
}