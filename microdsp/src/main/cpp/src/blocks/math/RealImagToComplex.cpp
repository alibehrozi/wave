#include "RealImagToComplex.h"
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "RealImagToComplex"

constexpr size_t MAX_MATH_CHUNK = 4096;

RealImagToComplex::RealImagToComplex(DataType type, const std::string& name)
    : Block(name), out_type_(type) {

    if (out_type_ == DataType::COMPLEX_DOUBLE) {
        in_type_ = DataType::DOUBLE;
        real_d_.resize(MAX_MATH_CHUNK);
        imag_d_.resize(MAX_MATH_CHUNK);
        out_cd_.resize(MAX_MATH_CHUNK);
    } else {
        out_type_ = DataType::COMPLEX_FLOAT;
        in_type_ = DataType::FLOAT;
        real_f_.resize(MAX_MATH_CHUNK);
        imag_f_.resize(MAX_MATH_CHUNK);
        out_cf_.resize(MAX_MATH_CHUNK);
    }

    add_input_port("real", in_type_);
    add_input_port("imag", in_type_);
    add_output_port("out", out_type_);
}

RealImagToComplex::~RealImagToComplex() {
    stop();
}

void RealImagToComplex::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void RealImagToComplex::work() {
    if (!is_active()) return;

    Port* in_real = get_input_port(0);
    Port* in_imag = get_input_port(1);
    Port* out = get_output_port(0);
    if (!in_real || !in_imag || !out || !in_real->get_buffer() || !in_imag->get_buffer()) return;

    size_t avail0 = in_real->get_buffer()->read_available();
    size_t avail1 = in_imag->get_buffer()->read_available();
    size_t available = std::min(avail0, avail1);
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_MATH_CHUNK);

    if (out_type_ == DataType::COMPLEX_FLOAT) {
        if (in_real->read(real_f_.data(), nitems) && in_imag->read(imag_f_.data(), nitems)) {
            for (size_t i = 0; i < nitems; ++i) {
                out_cf_[i] = std::complex<float>(real_f_[i], imag_f_[i]);
            }
            out->write(out_cf_.data(), nitems);
        }
    } else if (out_type_ == DataType::COMPLEX_DOUBLE) {
        if (in_real->read(real_d_.data(), nitems) && in_imag->read(imag_d_.data(), nitems)) {
            for (size_t i = 0; i < nitems; ++i) {
                out_cd_[i] = std::complex<double>(real_d_[i], imag_d_[i]);
            }
            out->write(out_cd_.data(), nitems);
        }
    }
}
