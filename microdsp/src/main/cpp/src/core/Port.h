#ifndef PORT_H
#define PORT_H

#include "Types.h"
#include "RingBuffer.h"
#include <memory>
#include <string>
#include <vector>
#include <mutex>
#include <limits>

// Forward declaration to avoid circular dependency
class Block;

/**
 * @class Port
 * @brief Represents an input or output connection point for a DSP block.
 *
 * Ports are the communication endpoints between Blocks. They handle:
 * - Data type negotiation (fixed or flexible types)
 * - Buffer management (ownership or sharing)
 * - Connection management (including fan-out support)
 */
class Port {
public:
    /**
     * @enum Direction
     * @brief Direction of data flow through the port
     */
    enum class Direction {
        INPUT,   /**< Port receives data (consumer) */
        OUTPUT   /**< Port sends data (producer) */
    };

    /**
     * @struct Config
     * @brief Configuration parameters for a port
     */
    struct Config {
        DataType type;                    /**< Current data type */
        std::vector<DataType> allowed_types; /**< Allowed data types (if flexible) */
        bool fixed_type = true;           /**< true: type is fixed, false: type can adapt */
        size_t min_items = 1;             /**< Minimum items per transfer */
        size_t max_items = 0;             /**< Maximum items (0 = unlimited) */
        size_t buffer_size = 8192;        /**< Buffer capacity in items */
    };

    /**
     * @brief Construct a new Port
     * @param parent The Block that owns this port
     * @param name Unique name within the parent block
     * @param config Port configuration
     * @param direction INPUT or OUTPUT
     */
    Port(Block* parent, const std::string& name, const Config& config, Direction direction);

    virtual ~Port();

    /**
     * @brief Connect this port to another port
     * @param other The port to connect to
     * @return true if connection successful
     */
    bool connect(Port* other);

    /**
     * @brief Disconnect this port from all connected ports
     */
    void disconnect();

    /**
     * @brief Disconnect from a specific port
     */
    void disconnect(Port* other);

    /**
     * @brief Check if this port is connected to any other port
     */
    bool is_connected() const;

    /**
     * @brief Check if this port is connected to a specific port
     */
    bool is_connected_to(const Port* other) const;

    /**
     * @brief Get all connected ports
     */
    std::vector<Port*> get_connections() const;

    /**
     * @brief Get the number of connected ports
     */
    size_t get_connection_count() const;

    /**
     * @brief Get the number of items available to read from this port
     */
    size_t read_available() const {
        RingBuffer* buf = get_buffer();
        return buf ? buf->read_available() : 0;
    }

    /**
     * @brief Get the number of items that can be written to this port
     */
    size_t write_available() const {
        if (direction_ == Direction::INPUT) {
            return input_buffer_ptr_ ? input_buffer_ptr_->write_available() : 0;
        }
        std::lock_guard<std::mutex> lock(mutex_);
        if (output_buffers_.empty()) return 0;
        size_t min_avail = std::numeric_limits<size_t>::max();
        for (const auto* buf : output_buffers_) {
            if (buf) {
                min_avail = std::min(min_avail, buf->write_available());
            }
        }
        return (min_avail == std::numeric_limits<size_t>::max()) ? 0 : min_avail;
    }

    /**
     * @brief Get the current buffer
     */
    RingBuffer* get_buffer() const;

    /**
     * @brief Read data from the port (type-safe)
     * @param data Buffer to read into
     * @param count Number of items to read
     * @return true if read was successful
     */
    template<typename T>
    bool read(T* data, size_t count) {
        RingBuffer* buf = get_buffer();
        if (!buf || type_to_enum<T>() != type_) return false;
        return buf->read(data, count);
    }

    /**
     * @brief Write data to the port (type-safe)
     * @param data Data to write
     * @param count Number of items to write
     * @return true if write was successful
     */
    template<typename T>
    bool write(const T* data, size_t count) {
        if (direction_ != Direction::OUTPUT || type_to_enum<T>() != type_) return false;
        bool all_success = true;
        std::lock_guard<std::mutex> lock(mutex_);
        if (output_buffers_.empty()) return false;
        for (auto* buf : output_buffers_) {
            if (!buf->write(data, count)) all_success = false;
        }
        return all_success;
    }

    /**
     * @brief Raw binary write (no type checking)
     */
    bool write_raw(const void* data, size_t count) {
        if (direction_ != Direction::OUTPUT) return false;
        bool all_success = true;
        std::lock_guard<std::mutex> lock(mutex_);
        if (output_buffers_.empty()) return false;
        for (auto* buf : output_buffers_) {
            if (!buf->write_raw(data, count)) all_success = false;
        }
        return all_success;
    }

    /**
     * @brief Raw binary read (no type checking)
     */
    bool read_raw(void* data, size_t count) {
        RingBuffer* buf = get_buffer();
        if (!buf) return false;
        return buf->read_raw(data, count);
    }

    /**
     * @brief Get the port name
     */
    const std::string& get_name() const { return name_; }

    /**
     * @brief Get the current data type
     */
    DataType get_type() const { return type_; }

    /**
     * @brief Get the port direction
     */
    Direction get_direction() const { return direction_; }

    /**
     * @brief Get the parent block
     */
    Block* get_parent() const { return parent_; }

    /**
     * @brief Get the number of items available to read from this port (type-safe)
     * @return Number of items available, or 0 if type mismatch
     */
    template<typename T>
    size_t items_available() const {
        if (type_to_enum<T>() != type_) return 0;
        return read_available();
    }

    // Internal methods for buffer management
    void set_buffer(std::unique_ptr<RingBuffer> buffer);
    void set_shared_buffer(RingBuffer* buffer);

    // Type negotiation and validation
    bool validate_connection(Port* other) const;
    bool can_handle_type(DataType type) const;
    bool can_adapt_to(DataType new_type) const;
    bool adapt_type(DataType new_type);

private:
    Block* parent_;
    std::string name_;
    DataType type_;
    Direction direction_;
    Config config_;

    std::vector<Port*> connected_ports_;
    mutable std::mutex mutex_;

    RingBuffer* input_buffer_ptr_ = nullptr;
    std::vector<RingBuffer*> output_buffers_;
    std::vector<std::unique_ptr<RingBuffer>> owned_buffers_;

    void clear_connections();
};

/**
 * @namespace port_config
 * @brief Helper functions for creating Port::Config objects
 */
namespace port_config {
    inline Port::Config fixed_type(DataType type, size_t buffer_size = 8192) {
        Port::Config config;
        config.type = type;
        config.buffer_size = buffer_size;
        config.fixed_type = true;
        config.allowed_types = {type};
        return config;
    }

    inline Port::Config any_type(size_t buffer_size = 8192) {
        Port::Config config;
        config.buffer_size = buffer_size;
        config.fixed_type = false;
        return config;
    }
}

#endif // PORT_H
