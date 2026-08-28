#include "JavaSource.h"
#include <algorithm>

constexpr size_t MAX_JAVA_TRANSFER_CHUNK = 8192;

JavaSource::JavaSource(DataType type, size_t buffer_capacity, const std::string& name)
    : Block(name), type_(type), item_size_(get_type_size(type)) {

    interop_buffer_ = std::make_unique<RingBuffer>(buffer_capacity, type);
    add_output_port("out", type);
    work_buffer_.resize(MAX_JAVA_TRANSFER_CHUNK * item_size_);
}

JavaSource::~JavaSource() {
    stop();
}

size_t JavaSource::push(const void* data, size_t count) {
    if (!data || count == 0) return 0;
    return interop_buffer_->write_raw(data, count) ? count : 0;
}

size_t JavaSource::write_available() const {
    return interop_buffer_->write_available();
}

void JavaSource::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    interop_buffer_->clear();
}

bool JavaSource::is_ready() {
    return is_active() && interop_buffer_->read_available() > 0;
}

void JavaSource::work() {
    if (!is_active()) return;

    Port* out = get_output_port(0);
    if (!out) return;

    size_t available = interop_buffer_->read_available();
    if (available == 0) return;

    size_t write_space = out->write_available();
    size_t nitems = std::min({available, write_space, MAX_JAVA_TRANSFER_CHUNK});
    if (nitems == 0) return;

    if (interop_buffer_->read_raw(work_buffer_.data(), nitems)) {
        out->write_raw(work_buffer_.data(), nitems);
    }
}
