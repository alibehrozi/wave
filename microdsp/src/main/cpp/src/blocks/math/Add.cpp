#include "Add.h"
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Add"

constexpr size_t MAX_MATH_CHUNK = 4096;

Add::Add(DataType type, size_t num_inputs, const std::string& name)
    : Block(name), type_(type), num_inputs_(std::max(size_t(1), num_inputs)) {

    for (size_t i = 0; i < num_inputs_; ++i) {
        add_input_port("in" + std::to_string(i), type);
    }
    add_output_port("out", type);

    switch (type_) {
        case DataType::FLOAT:
            bufs_f_.resize(num_inputs_, std::vector<float>(MAX_MATH_CHUNK));
            out_f_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::COMPLEX_FLOAT:
            bufs_cf_.resize(num_inputs_, std::vector<std::complex<float>>(MAX_MATH_CHUNK));
            out_cf_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::DOUBLE:
            bufs_d_.resize(num_inputs_, std::vector<double>(MAX_MATH_CHUNK));
            out_d_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::COMPLEX_DOUBLE:
            bufs_cd_.resize(num_inputs_, std::vector<std::complex<double>>(MAX_MATH_CHUNK));
            out_cd_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::INT32:
            bufs_i32_.resize(num_inputs_, std::vector<int32_t>(MAX_MATH_CHUNK));
            out_i32_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::SHORT:
            bufs_i16_.resize(num_inputs_, std::vector<int16_t>(MAX_MATH_CHUNK));
            out_i16_.resize(MAX_MATH_CHUNK);
            break;
        default:
            bufs_f_.resize(num_inputs_, std::vector<float>(MAX_MATH_CHUNK));
            out_f_.resize(MAX_MATH_CHUNK);
            break;
    }
}

Add::~Add() {
    stop();
}

void Add::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void Add::work() {
    if (!is_active()) return;

    Port* out = get_output_port(0);
    if (!out) return;

    size_t min_available = std::numeric_limits<size_t>::max();
    for (size_t i = 0; i < num_inputs_; ++i) {
        Port* in = get_input_port(i);
        if (!in || !in->get_buffer()) return;
        size_t avail = in->get_buffer()->read_available();
        min_available = std::min(min_available, avail);
    }

    if (min_available == 0 || min_available == std::numeric_limits<size_t>::max()) return;
    size_t nitems = std::min(min_available, MAX_MATH_CHUNK);

    if (type_ == DataType::FLOAT) {
        for (size_t i = 0; i < num_inputs_; ++i) {
            get_input_port(i)->read(bufs_f_[i].data(), nitems);
        }
        process(bufs_f_, out_f_.data(), nitems);
        out->write(out_f_.data(), nitems);
    } else if (type_ == DataType::COMPLEX_FLOAT) {
        for (size_t i = 0; i < num_inputs_; ++i) {
            get_input_port(i)->read(bufs_cf_[i].data(), nitems);
        }
        process(bufs_cf_, out_cf_.data(), nitems);
        out->write(out_cf_.data(), nitems);
    } else if (type_ == DataType::DOUBLE) {
        for (size_t i = 0; i < num_inputs_; ++i) {
            get_input_port(i)->read(bufs_d_[i].data(), nitems);
        }
        process(bufs_d_, out_d_.data(), nitems);
        out->write(out_d_.data(), nitems);
    } else if (type_ == DataType::COMPLEX_DOUBLE) {
        for (size_t i = 0; i < num_inputs_; ++i) {
            get_input_port(i)->read(bufs_cd_[i].data(), nitems);
        }
        process(bufs_cd_, out_cd_.data(), nitems);
        out->write(out_cd_.data(), nitems);
    } else if (type_ == DataType::INT32) {
        for (size_t i = 0; i < num_inputs_; ++i) {
            get_input_port(i)->read(bufs_i32_[i].data(), nitems);
        }
        process(bufs_i32_, out_i32_.data(), nitems);
        out->write(out_i32_.data(), nitems);
    } else if (type_ == DataType::SHORT) {
        for (size_t i = 0; i < num_inputs_; ++i) {
            get_input_port(i)->read(bufs_i16_[i].data(), nitems);
        }
        process(bufs_i16_, out_i16_.data(), nitems);
        out->write(out_i16_.data(), nitems);
    }
}

template<typename T>
void Add::process(const std::vector<std::vector<T>>& in_bufs, T* out, size_t nitems) {
    for (size_t j = 0; j < nitems; ++j) {
        T sum = in_bufs[0][j];
        for (size_t i = 1; i < num_inputs_; ++i) {
            sum += in_bufs[i][j];
        }
        out[j] = sum;
    }
}
