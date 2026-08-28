#include "ComplexToRealImag.h"
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "ComplexToRealImag"

constexpr size_t MAX_MATH_CHUNK = 4096;

ComplexToRealImag::ComplexToRealImag(DataType type, const std::string& name)
    : Block(name), in_type_(type) {

    if (in_type_ == DataType::COMPLEX_DOUBLE) {
        out_type_ = DataType::DOUBLE;
        in_cd_.resize(MAX_MATH_CHUNK);
        real_d_.resize(MAX_MATH_CHUNK);
        imag_d_.resize(MAX_MATH_CHUNK);
    } else {
        in_type_ = DataType::COMPLEX_FLOAT;
        out_type_ = DataType::FLOAT;
        in_cf_.resize(MAX_MATH_CHUNK);
        real_f_.resize(MAX_MATH_CHUNK);
        imag_f_.resize(MAX_MATH_CHUNK);
    }

    add_input_port("in", in_type_);
    add_output_port("real", out_type_);
    add_output_port("imag", out_type_);
}

ComplexToRealImag::~ComplexToRealImag() {
    stop();
}

void ComplexToRealImag::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void ComplexToRealImag::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out_real = get_output_port(0);
    Port* out_imag = get_output_port(1);
    if (!in || !out_real || !out_imag || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_MATH_CHUNK);

    if (in_type_ == DataType::COMPLEX_FLOAT) {
        if (in->read(in_cf_.data(), nitems)) {
            for (size_t i = 0; i < nitems; ++i) {
                real_f_[i] = in_cf_[i].real();
                imag_f_[i] = in_cf_[i].imag();
            }
            out_real->write(real_f_.data(), nitems);
            out_imag->write(imag_f_.data(), nitems);
        }
    } else if (in_type_ == DataType::COMPLEX_DOUBLE) {
        if (in->read(in_cd_.data(), nitems)) {
            for (size_t i = 0; i < nitems; ++i) {
                real_d_[i] = in_cd_[i].real();
                imag_d_[i] = in_cd_[i].imag();
            }
            out_real->write(real_d_.data(), nitems);
            out_imag->write(imag_d_.data(), nitems);
        }
    }
}
