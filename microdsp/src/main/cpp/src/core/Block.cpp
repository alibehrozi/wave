#include "Block.h"
#include <algorithm>
#include <stdexcept>
#include <android/log.h>

#define LOG_TAG "Block"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

Block::Block(const std::string& name)
        : name_(name), active_(false) {
    LOGI("Block created: %s", name.c_str());
}

Block::~Block() {
    LOGI("Block destroyed: %s", name_.c_str());

    // Stop if active using a non-virtual call to avoid UB.
    // Derived destructors are responsible for calling their own stop() first.
    if (active_.load(std::memory_order_acquire)) {
        Block::stop();
    }

    // Disconnect all ports
    disconnect_all_ports();
}

int Block::start() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (active_.exchange(true, std::memory_order_acq_rel)) {
        LOGI("Block '%s' already started", name_.c_str());
        return 0; // Already active — treat as success/no-op
    }

    LOGI("Block '%s' started", name_.c_str());
    return 0;
}

void Block::stop() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!active_.exchange(false, std::memory_order_acq_rel)) {
        return; // Already stopped
    }

    LOGI("Block '%s' stopped", name_.c_str());
}

Port* Block::add_input_port(const std::string& name, const Port::Config& config) {
    std::lock_guard<std::mutex> lock(mutex_);

    // Check for duplicate name
    for (const auto& port : input_ports_) {
        if (port->get_name() == name) {
            LOGE("Input port '%s' already exists in block '%s'",
                 name.c_str(), name_.c_str());
            return nullptr;
        }
    }

    auto port = std::make_unique<Port>(this, name, config, Port::Direction::INPUT);
    auto* ptr = port.get();
    input_ports_.push_back(std::move(port));

    LOGI("Added input port '%s' to block '%s'", name.c_str(), name_.c_str());
    return ptr;
}

Port* Block::add_output_port(const std::string& name, const Port::Config& config) {
    std::lock_guard<std::mutex> lock(mutex_);

    // Check for duplicate name
    for (const auto& port : output_ports_) {
        if (port->get_name() == name) {
            LOGE("Output port '%s' already exists in block '%s'",
                 name.c_str(), name_.c_str());
            return nullptr;
        }
    }

    auto port = std::make_unique<Port>(this, name, config, Port::Direction::OUTPUT);
    auto* ptr = port.get();
    output_ports_.push_back(std::move(port));

    LOGI("Added output port '%s' to block '%s'", name.c_str(), name_.c_str());
    return ptr;
}

Port* Block::add_flexible_input_port(const std::string& name,
                                     const std::vector<DataType>& allowed_types,
                                     size_t buffer_size) {
    Port::Config config = port_config::any_type(buffer_size);
    config.allowed_types = allowed_types;
    config.fixed_type = false;
    return add_input_port(name, config);
}

Port* Block::add_flexible_output_port(const std::string& name,
                                      const std::vector<DataType>& allowed_types,
                                      size_t buffer_size) {
    Port::Config config = port_config::any_type(buffer_size);
    config.allowed_types = allowed_types;
    config.fixed_type = false;
    return add_output_port(name, config);
}

Port* Block::get_input_port(const std::string& name) const {
    std::lock_guard<std::mutex> lock(mutex_);

    auto it = std::find_if(input_ports_.begin(), input_ports_.end(),
                           [&name](const auto& port) {
                               return port->get_name() == name;
                           });
    return it != input_ports_.end() ? it->get() : nullptr;
}

Port* Block::get_output_port(const std::string& name) const {
    std::lock_guard<std::mutex> lock(mutex_);

    auto it = std::find_if(output_ports_.begin(), output_ports_.end(),
                           [&name](const auto& port) {
                               return port->get_name() == name;
                           });
    return it != output_ports_.end() ? it->get() : nullptr;
}

Port* Block::get_input_port(size_t index) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return index < input_ports_.size() ? input_ports_[index].get() : nullptr;
}

Port* Block::get_output_port(size_t index) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return index < output_ports_.size() ? output_ports_[index].get() : nullptr;
}

std::string Block::get_input_port_name(size_t index) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (index < input_ports_.size()) {
        return input_ports_[index]->get_name();
    }
    return "";
}

std::string Block::get_output_port_name(size_t index) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (index < output_ports_.size()) {
        return output_ports_[index]->get_name();
    }
    return "";
}

void Block::disconnect_all_ports() {
    std::lock_guard<std::mutex> lock(mutex_);

    for (auto& port : input_ports_) {
        if (port) {
            port->disconnect();
        }
    }
    for (auto& port : output_ports_) {
        if (port) {
            port->disconnect();
        }
    }
    LOGI("All ports disconnected for block '%s'", name_.c_str());
}

int Block::get_int_parameter(const std::string& name, int default_val) const {
    std::lock_guard<std::mutex> lock(param_mutex_);
    auto it = int_params_.find(name);
    return it != int_params_.end() ? it->second : default_val;
}

double Block::get_double_parameter(const std::string& name, double default_val) const {
    std::lock_guard<std::mutex> lock(param_mutex_);
    auto it = double_params_.find(name);
    return it != double_params_.end() ? it->second : default_val;
}

std::string Block::get_string_parameter(const std::string& name,
                                        const std::string& default_val) const {
    std::lock_guard<std::mutex> lock(param_mutex_);
    auto it = string_params_.find(name);
    return it != string_params_.end() ? it->second : default_val;
}

bool Block::get_bool_parameter(const std::string& name, bool default_val) const {
    std::lock_guard<std::mutex> lock(param_mutex_);
    auto it = bool_params_.find(name);
    return it != bool_params_.end() ? it->second : default_val;
}

bool Block::has_parameter(const std::string& name) const {
    std::lock_guard<std::mutex> lock(param_mutex_);
    return int_params_.find(name) != int_params_.end() ||
           double_params_.find(name) != double_params_.end() ||
           string_params_.find(name) != string_params_.end() ||
           bool_params_.find(name) != bool_params_.end();
}

std::vector<std::string> Block::get_parameter_names() const {
    std::lock_guard<std::mutex> lock(param_mutex_);

    // Use a set to ensure uniqueness
    std::set<std::string> names;
    for (const auto& p : int_params_) names.insert(p.first);
    for (const auto& p : double_params_) names.insert(p.first);
    for (const auto& p : string_params_) names.insert(p.first);
    for (const auto& p : bool_params_) names.insert(p.first);

    return std::vector<std::string>(names.begin(), names.end());
}

void Block::reset_parameters() {
    std::lock_guard<std::mutex> lock(param_mutex_);

    int_params_.clear();
    double_params_.clear();
    string_params_.clear();
    bool_params_.clear();

    LOGI("Parameters reset for block '%s'", name_.c_str());
}