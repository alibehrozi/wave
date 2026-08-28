#include "SquelchBlock.h"
#include <cmath>
#include <algorithm>

SquelchBlock::SquelchBlock(DataType type, float threshold_db, const std::string& name)
    : Block(name), type_(type), threshold_linear_(0.0f) {
    set_threshold(threshold_db);
    add_input_port("in", type);
    add_output_port("out", type);
    work_buf_.resize(8192 * get_type_size(type));
}

void SquelchBlock::set_threshold(float db) {
    threshold_linear_ = std::pow(10.0f, db / 20.0f);
}

void SquelchBlock::reset() {
    current_power_ = 0.0f;
    open_ = false;
}

void SquelchBlock::work() {
    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out) return;

    size_t nitems = std::min(in->read_available(), out->write_available());
    if (nitems == 0) return;

    if (in->read_raw(work_buf_.data(), nitems)) {
        float batch_power = 0.0f;

        if (type_ == DataType::COMPLEX_FLOAT) {
            auto* data_cf = reinterpret_cast<std::complex<float>*>(work_buf_.data());
            for (size_t i = 0; i < nitems; ++i) {
                batch_power += std::abs(data_cf[i]);
            }
            batch_power /= static_cast<float>(nitems);

            current_power_ = (1.0f - alpha_) * current_power_ + alpha_ * batch_power;
            bool should_open = current_power_ > threshold_linear_;
            open_.store(should_open);

            if (!should_open) {
                std::fill_n(data_cf, nitems, std::complex<float>(0.0f, 0.0f));
            }
        } else if (type_ == DataType::FLOAT) {
            auto* data_f = reinterpret_cast<float*>(work_buf_.data());
            for (size_t i = 0; i < nitems; ++i) {
                batch_power += std::abs(data_f[i]);
            }
            batch_power /= static_cast<float>(nitems);

            current_power_ = (1.0f - alpha_) * current_power_ + alpha_ * batch_power;
            bool should_open = current_power_ > threshold_linear_;
            open_.store(should_open);

            if (!should_open) {
                std::fill_n(data_f, nitems, 0.0f);
            }
        } else if (type_ == DataType::SHORT) {
            auto* data_s = reinterpret_cast<int16_t*>(work_buf_.data());
            for (size_t i = 0; i < nitems; ++i) {
                batch_power += std::abs(static_cast<float>(data_s[i]) / 32768.0f);
            }
            batch_power /= static_cast<float>(nitems);

            current_power_ = (1.0f - alpha_) * current_power_ + alpha_ * batch_power;
            bool should_open = current_power_ > threshold_linear_;
            open_.store(should_open);

            if (!should_open) {
                std::fill_n(data_s, nitems, static_cast<int16_t>(0));
            }
        }

        out->write_raw(work_buf_.data(), nitems);
    }
}
