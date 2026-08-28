#include "Port.h"
#include "Block.h"
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "Port"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

Port::Port(Block* parent, const std::string& name, const Config& config, Direction direction)
        : parent_(parent), name_(name), type_(config.type),
          direction_(direction), config_(config) {

    if (config_.allowed_types.empty() && config_.fixed_type) {
        config_.allowed_types = {config.type};
    }

    LOGI("Port created: %s:%s (%s)",
         parent_ ? parent_->get_name().c_str() : "null",
         name_.c_str(),
         direction_ == Direction::OUTPUT ? "OUTPUT" : "INPUT");
}

Port::~Port() {
    disconnect();
}

bool Port::connect(Port* other) {
    if (!other) {
        LOGE("Port::connect - other port is null");
        return false;
    }

    // Acquire both mutexes simultaneously to prevent data races when two
    // threads connect/disconnect the same pair of ports concurrently.
    std::unique_lock<std::mutex> lock1(mutex_, std::defer_lock);
    std::unique_lock<std::mutex> lock2(other->mutex_, std::defer_lock);
    std::lock(lock1, lock2);

    if (!validate_connection(other)) {
        LOGE("Port::connect - validation failed");
        return false;
    }

    // Check if already connected to this port
    if (std::find(connected_ports_.begin(), connected_ports_.end(), other) != connected_ports_.end()) {
        LOGI("Port::connect - already connected to %s", other->name_.c_str());
        return true;
    }

    // For INPUT ports: can only connect to one OUTPUT
    if (direction_ == Direction::INPUT && !connected_ports_.empty()) {
        LOGE("Port::connect - INPUT port can only have one connection (no fan-in)");
        return false;
    }

    // For other's INPUT port: check if already connected
    if (other->direction_ == Direction::INPUT && !other->connected_ports_.empty()) {
        LOGE("Port::connect - other INPUT port already connected (no fan-in)");
        return false;
    }

    // Determine final type for the connection
    DataType final_type = type_;
    if (!config_.fixed_type && other->config_.fixed_type) {
        if (!adapt_type(other->type_)) {
            LOGE("Port::connect - cannot adapt type");
            return false;
        }
        final_type = other->type_;
    } else if (config_.fixed_type && !other->config_.fixed_type) {
        if (!other->adapt_type(type_)) {
            LOGE("Port::connect - other cannot adapt type");
            return false;
        }
        final_type = type_;
    } else if (!config_.fixed_type && !other->config_.fixed_type) {
        bool found_common_type = false;
        for (DataType type1 : config_.allowed_types) {
            for (DataType type2 : other->config_.allowed_types) {
                if (type1 == type2) {
                    adapt_type(type1);
                    other->adapt_type(type1);
                    final_type = type1;
                    found_common_type = true;
                    break;
                }
            }
            if (found_common_type) break;
        }
        if (!found_common_type && !config_.allowed_types.empty() && !other->config_.allowed_types.empty()) {
            LOGE("Port::connect - no common type found");
            return false;
        }
    }

    // Create new buffer for this connection
    size_t buffer_size = std::max(config_.buffer_size, other->config_.buffer_size);
    auto buffer = std::make_unique<RingBuffer>(buffer_size, final_type);
    RingBuffer* buffer_ptr = buffer.get();

    if (direction_ == Direction::OUTPUT) {
        output_buffers_.push_back(buffer_ptr);
        owned_buffers_.push_back(std::move(buffer));
        other->input_buffer_ptr_ = buffer_ptr;
    } else {
        input_buffer_ptr_ = buffer_ptr;
        other->output_buffers_.push_back(buffer_ptr);
        other->owned_buffers_.push_back(std::move(buffer));
    }

    // Add to connection lists
    connected_ports_.push_back(other);
    other->connected_ports_.push_back(this);

    LOGI("Port::connect SUCCESS: %s -> %s", name_.c_str(), other->name_.c_str());
    return true;
}

void Port::disconnect() {
    // Acquire all connected ports' mutexes would require knowing them in advance.
    // Instead, lock this port first and snapshot connected ports, then update each.
    std::vector<Port*> ports_to_update;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        ports_to_update = connected_ports_;
    }

    for (Port* other : ports_to_update) {
        // Lock both ports to safely update the other side's state.
        std::unique_lock<std::mutex> lock1(mutex_, std::defer_lock);
        std::unique_lock<std::mutex> lock2(other->mutex_, std::defer_lock);
        std::lock(lock1, lock2);

        auto it = std::find(other->connected_ports_.begin(), other->connected_ports_.end(), this);
        if (it != other->connected_ports_.end()) {
            other->connected_ports_.erase(it);
        }

        // Clean up buffers in other port
        if (other->direction_ == Direction::INPUT) {
            other->input_buffer_ptr_ = nullptr;
        } else {
            // Remove our input buffer from their output list
            auto buf_it = std::find(other->output_buffers_.begin(), other->output_buffers_.end(), input_buffer_ptr_);
            if (buf_it != other->output_buffers_.end()) {
                other->output_buffers_.erase(buf_it);
            }
        }
    }

    std::lock_guard<std::mutex> lock(mutex_);
    connected_ports_.clear();
    owned_buffers_.clear();
    output_buffers_.clear();
    input_buffer_ptr_ = nullptr;

    LOGI("Port::disconnect - %s", name_.c_str());
}

void Port::disconnect(Port* other) {
    if (!other) return;

    // Acquire both port mutexes simultaneously to prevent data races.
    std::unique_lock<std::mutex> lock1(mutex_, std::defer_lock);
    std::unique_lock<std::mutex> lock2(other->mutex_, std::defer_lock);
    std::lock(lock1, lock2);

    // Remove from this port's connections
    auto it = std::find(connected_ports_.begin(), connected_ports_.end(), other);
    if (it != connected_ports_.end()) {
        connected_ports_.erase(it);

        // Find and remove associated buffer
        if (direction_ == Direction::OUTPUT) {
            // We need to find which unique_ptr in owned_buffers_ corresponds to 'other'
            // In our current design, we don't have a direct map from port to buffer.
            // But we know 'other' (INPUT) has input_buffer_ptr_ pointing to it.
            RingBuffer* buf_ptr = other->input_buffer_ptr_;
            if (buf_ptr) {
                auto buf_it = std::find(output_buffers_.begin(), output_buffers_.end(), buf_ptr);
                if (buf_it != output_buffers_.end()) {
                    output_buffers_.erase(buf_it);
                }

                auto owned_it = std::find_if(owned_buffers_.begin(), owned_buffers_.end(),
                                             [buf_ptr](const auto& b) { return b.get() == buf_ptr; });
                if (owned_it != owned_buffers_.end()) {
                    owned_buffers_.erase(owned_it);
                }
                other->input_buffer_ptr_ = nullptr;
            }
        } else {
            // We are INPUT, 'other' is OUTPUT
            // The buffer is owned by 'other'
            RingBuffer* buf_ptr = input_buffer_ptr_;
            auto buf_it = std::find(other->output_buffers_.begin(), other->output_buffers_.end(), buf_ptr);
            if (buf_it != other->output_buffers_.end()) {
                other->output_buffers_.erase(buf_it);
            }

            auto owned_it = std::find_if(other->owned_buffers_.begin(), other->owned_buffers_.end(),
                                         [buf_ptr](const auto& b) { return b.get() == buf_ptr; });
            if (owned_it != other->owned_buffers_.end()) {
                other->owned_buffers_.erase(owned_it);
            }
            input_buffer_ptr_ = nullptr;
        }
    }

    // Remove from other port's connections
    auto other_it = std::find(other->connected_ports_.begin(), other->connected_ports_.end(), this);
    if (other_it != other->connected_ports_.end()) {
        other->connected_ports_.erase(other_it);
    }

    LOGI("Port::disconnect - %s from %s", name_.c_str(), other->name_.c_str());
}

bool Port::is_connected() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return !connected_ports_.empty();
}

bool Port::is_connected_to(const Port* other) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!other) return false;
    return std::find(connected_ports_.begin(), connected_ports_.end(), other) != connected_ports_.end();
}

std::vector<Port*> Port::get_connections() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return connected_ports_;
}

size_t Port::get_connection_count() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return connected_ports_.size();
}

void Port::set_buffer(std::unique_ptr<RingBuffer> buffer) {
    RingBuffer* ptr = buffer.get();
    owned_buffers_.push_back(std::move(buffer));
    if (direction_ == Direction::OUTPUT) {
        output_buffers_.push_back(ptr);
    } else {
        input_buffer_ptr_ = ptr;
    }
}

void Port::set_shared_buffer(RingBuffer* buffer) {
    if (direction_ == Direction::INPUT) {
        input_buffer_ptr_ = buffer;
    } else {
        output_buffers_.push_back(buffer);
    }
}

RingBuffer* Port::get_buffer() const {
    if (direction_ == Direction::INPUT) {
        return input_buffer_ptr_;
    }
    // For OUTPUT ports, there isn't a single buffer anymore.
    // This method is kept for compatibility but should be used carefully.
    return output_buffers_.empty() ? nullptr : output_buffers_[0];
}

bool Port::validate_connection(Port* other) const {
    if (!other) return false;

    if (direction_ == other->direction_) {
        LOGE("Port::validate_connection - same direction");
        return false;
    }

    if (config_.fixed_type && other->config_.fixed_type) {
        if (type_ != other->type_) {
            LOGE("Port::validate_connection - fixed type mismatch");
            return false;
        }
    } else if (config_.fixed_type && !other->config_.fixed_type) {
        if (!other->can_handle_type(type_)) {
            LOGE("Port::validate_connection - other cannot handle type");
            return false;
        }
    } else if (!config_.fixed_type && other->config_.fixed_type) {
        if (!can_handle_type(other->type_)) {
            LOGE("Port::validate_connection - cannot handle other type");
            return false;
        }
    }

    return true;
}

bool Port::can_handle_type(DataType type) const {
    if (config_.allowed_types.empty()) {
        return true;
    }
    return std::find(config_.allowed_types.begin(), config_.allowed_types.end(), type)
           != config_.allowed_types.end();
}

bool Port::can_adapt_to(DataType new_type) const {
    return !config_.fixed_type && can_handle_type(new_type);
}

bool Port::adapt_type(DataType new_type) {
    if (!can_adapt_to(new_type)) {
        LOGE("Port::adapt_type - cannot adapt to type");
        return false;
    }
    type_ = new_type;
    return true;
}