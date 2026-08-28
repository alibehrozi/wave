#include "JavaSink.h"
#include <algorithm>

constexpr size_t MAX_JAVA_SINK_TRANSFER_CHUNK = 8192;

JavaSink::JavaSink(DataType type, size_t buffer_capacity, const std::string& name)
    : Block(name), type_(type), item_size_(get_type_size(type)) {

    interop_buffer_ = std::make_unique<RingBuffer>(buffer_capacity, type);
    add_input_port("in", type);
    work_buffer_.resize(MAX_JAVA_SINK_TRANSFER_CHUNK * item_size_);
}

JavaSink::~JavaSink() {
    stop();
}

size_t JavaSink::pull(void* data, size_t max_count) {
    if (!data || max_count == 0) return 0;

    size_t available = interop_buffer_->read_available();
    size_t nitems = std::min(available, max_count);
    if (nitems == 0) return 0;

    return interop_buffer_->read_raw(data, nitems) ? nitems : 0;
}

size_t JavaSink::read_available() const {
    return interop_buffer_->read_available();
}

void JavaSink::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    interop_buffer_->clear();
}

bool JavaSink::is_ready() {
    Port* in = get_input_port(0);
    return is_active() && in && in->read_available() > 0;
}

void JavaSink::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    if (!in) return;

    size_t available = in->read_available();
    if (available == 0) return;

    size_t write_space = interop_buffer_->write_available();
    size_t nitems = std::min({available, write_space, MAX_JAVA_SINK_TRANSFER_CHUNK});
    if (nitems == 0) return;

    if (in->read_raw(work_buffer_.data(), nitems)) {
        interop_buffer_->write_raw(work_buffer_.data(), nitems);
    }
}
