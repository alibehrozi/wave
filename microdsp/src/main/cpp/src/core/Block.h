#ifndef BLOCK_H
#define BLOCK_H

#include "Port.h"
#include "Types.h"
#include <vector>
#include <memory>
#include <string>
#include <unordered_map>
#include <atomic>
#include <functional>
#include <mutex>
#include <set>

/**
 * Base class for all DSP processing blocks.
 * A Block is the fundamental processing unit that performs signal processing.
 * Each block can have input/output ports and configurable parameters.
 */
class Block {
public:
    using sptr = std::shared_ptr<Block>;

    /**
     * Create a new block with the given name
     * @param name Unique name for this block
     */
    Block(const std::string& name);

    /**
     * Virtual destructor - ensures proper cleanup of derived classes
     */
    virtual ~Block();

    /**
     * Start the block processing
     * @return true if started successfully
     */
    virtual int start();

    /**
     * Stop the block processing
     */
    virtual void stop();

    /**
     * Reset internal state (history buffers, phase, etc.)
     */
    virtual void reset() {}

    /**
     * Check if the block is ready to perform work.
     * Default implementation returns true if active.
     * Subclasses should override this to check port conditions.
     */
    virtual bool is_ready() {
        return is_active();
    }

    /**
     * Perform the actual signal processing work.
     * This must be implemented by derived classes.
     */
    virtual void work() = 0;

    /**
     * Check if the block is currently active (processing)
     * @return true if active
     */
    bool is_active() const {
        return active_.load(std::memory_order_acquire);
    }

    /**
        * @brief Create native block for JNI
        * @param name Block name
        * @return Native handle (typically 'this' pointer)
        *
        * This method is used by JNI to get a handle to the native block object.
        * Subclasses should implement this to return their native pointer.
    */
    virtual int64_t nativeCreateBlock(const std::string& name) {
        // Default implementation returns the 'this' pointer
        return reinterpret_cast<int64_t>(this);
    }

    /**
     * Add an input port to this block
     * @param name Port name (must be unique)
     * @param config Port configuration
     * @return Pointer to the created port, or nullptr on failure
     */
    Port* add_input_port(const std::string& name, const Port::Config& config);

    /**
     * Add an output port to this block
     * @param name Port name (must be unique)
     * @param config Port configuration
     * @return Pointer to the created port, or nullptr on failure
     */
    Port* add_output_port(const std::string& name, const Port::Config& config);

    /**
     * Add an input port with fixed data type
     * @param name Port name
     * @param dtype Data type for this port
     * @param buffer_size Buffer size in items
     * @return Pointer to the created port
     */
    Port* add_input_port(const std::string& name, DataType dtype, size_t buffer_size = 8192) {
        return add_input_port(name, port_config::fixed_type(dtype, buffer_size));
    }

    /**
     * Add an output port with fixed data type
     * @param name Port name
     * @param dtype Data type for this port
     * @param buffer_size Buffer size in items
     * @return Pointer to the created port
     */
    Port* add_output_port(const std::string& name, DataType dtype, size_t buffer_size = 8192) {
        return add_output_port(name, port_config::fixed_type(dtype, buffer_size));
    }

    /**
     * Add an input port with template type deduction
     * @param name Port name
     * @param buffer_size Buffer size in items
     * @return Pointer to the created port
     */
    template<typename T>
    Port* add_input_port(const std::string& name, size_t buffer_size = 8192) {
        return add_input_port(name, port_config::fixed_type(type_to_enum<T>(), buffer_size));
    }

    /**
     * Add an output port with template type deduction
     * @param name Port name
     * @param buffer_size Buffer size in items
     * @return Pointer to the created port
     */
    template<typename T>
    Port* add_output_port(const std::string& name, size_t buffer_size = 8192) {
        return add_output_port(name, port_config::fixed_type(type_to_enum<T>(), buffer_size));
    }

    /**
     * Add a flexible input port that can accept multiple data types
     * @param name Port name
     * @param allowed_types Vector of allowed data types
     * @param buffer_size Buffer size in items
     * @return Pointer to the created port
     */
    Port* add_flexible_input_port(const std::string& name,
                                  const std::vector<DataType>& allowed_types,
                                  size_t buffer_size = 8192);

    /**
     * Add a flexible output port that can output multiple data types
     * @param name Port name
     * @param allowed_types Vector of allowed data types
     * @param buffer_size Buffer size in items
     * @return Pointer to the created port
     */
    Port* add_flexible_output_port(const std::string& name,
                                   const std::vector<DataType>& allowed_types,
                                   size_t buffer_size = 8192);

    /**
     * Get input port by name
     * @param name Port name
     * @return Pointer to the port, or nullptr if not found
     */
    Port* get_input_port(const std::string& name) const;

    /**
     * Get output port by name
     * @param name Port name
     * @return Pointer to the port, or nullptr if not found
     */
    Port* get_output_port(const std::string& name) const;

    /**
     * Get input port by index
     * @param index Port index (0-based)
     * @return Pointer to the port, or nullptr if index is invalid
     */
    Port* get_input_port(size_t index) const;

    /**
     * Get output port by index
     * @param index Port index (0-based)
     * @return Pointer to the port, or nullptr if index is invalid
     */
    Port* get_output_port(size_t index) const;

    /**
     * Get input port name by index (for JNI)
     * @param index Port index (0-based)
     * @return Port name, or empty string if index is invalid
     */
    std::string get_input_port_name(size_t index) const;

    /**
     * Get output port name by index (for JNI)
     * @param index Port index (0-based)
     * @return Port name, or empty string if index is invalid
     */
    std::string get_output_port_name(size_t index) const;

    /**
     * Get all input ports
     * @return Const reference to the vector of input ports
     */
    const std::vector<std::unique_ptr<Port>>& get_input_ports() const {
        return input_ports_;
    }

    /**
     * Get all output ports
     * @return Const reference to the vector of output ports
     */
    const std::vector<std::unique_ptr<Port>>& get_output_ports() const {
        return output_ports_;
    }

    /**
     * Get number of input ports
     * @return Input port count
     */
    size_t get_input_port_count() const { return input_ports_.size(); }

    /**
     * Get number of output ports
     * @return Output port count
     */
    size_t get_output_port_count() const { return output_ports_.size(); }

    /**
     * Disconnect all ports (useful for cleanup)
     */
    void disconnect_all_ports();

    /**
     * Set integer parameter
     * @param name Parameter name
     * @param value Parameter value
     */
    void set_parameter(const std::string& name, int value) {
        std::lock_guard<std::mutex> lock(param_mutex_);
        int_params_[name] = value;
    }

    /**
     * Set double parameter
     * @param name Parameter name
     * @param value Parameter value
     */
    void set_parameter(const std::string& name, double value) {
        std::lock_guard<std::mutex> lock(param_mutex_);
        double_params_[name] = value;
    }

    /**
     * Set string parameter
     * @param name Parameter name
     * @param value Parameter value
     */
    void set_parameter(const std::string& name, const std::string& value) {
        std::lock_guard<std::mutex> lock(param_mutex_);
        string_params_[name] = value;
    }

    /**
     * Set boolean parameter
     * @param name Parameter name
     * @param value Parameter value
     */
    void set_parameter(const std::string& name, bool value) {
        std::lock_guard<std::mutex> lock(param_mutex_);
        bool_params_[name] = value;
    }

    /**
     * Get integer parameter with default
     * @param name Parameter name
     * @param default_val Default value if parameter not found
     * @return Parameter value or default
     */
    int get_int_parameter(const std::string& name, int default_val = 0) const;

    /**
     * Get double parameter with default
     * @param name Parameter name
     * @param default_val Default value if parameter not found
     * @return Parameter value or default
     */
    double get_double_parameter(const std::string& name, double default_val = 0.0) const;

    /**
     * Get string parameter with default
     * @param name Parameter name
     * @param default_val Default value if parameter not found
     * @return Parameter value or default
     */
    std::string get_string_parameter(const std::string& name,
                                     const std::string& default_val = "") const;

    /**
     * Get boolean parameter with default
     * @param name Parameter name
     * @param default_val Default value if parameter not found
     * @return Parameter value or default
     */
    bool get_bool_parameter(const std::string& name, bool default_val = false) const;

    /**
     * Check if a parameter exists
     * @param name Parameter name
     * @return true if parameter exists
     */
    bool has_parameter(const std::string& name) const;

    /**
     * Get all parameter names
     * @return Vector of parameter names
     */
    std::vector<std::string> get_parameter_names() const;

    /**
     * Reset all parameters to their default values
     */
    void reset_parameters();

    /**
     * Get the block name
     * @return Block name
     */
    const std::string& get_name() const { return name_; }

    /**
     * Get the block's native handle (for JNI)
     * @return Native handle (usually the 'this' pointer)
     */
    int64_t get_native_handle() const { return reinterpret_cast<int64_t>(this); }

protected:
    std::string name_;
    std::atomic<bool> active_;

    std::vector<std::unique_ptr<Port>> input_ports_;
    std::vector<std::unique_ptr<Port>> output_ports_;

    // Parameter storage with mutex for thread safety
    mutable std::mutex param_mutex_;
    std::unordered_map<std::string, int> int_params_;
    std::unordered_map<std::string, double> double_params_;
    std::unordered_map<std::string, std::string> string_params_;
    std::unordered_map<std::string, bool> bool_params_;

    // Mutex for thread safety
    mutable std::mutex mutex_;
};

// Factory function type for block creation
using BlockFactory = std::function<std::shared_ptr<Block>()>;

#endif // BLOCK_H