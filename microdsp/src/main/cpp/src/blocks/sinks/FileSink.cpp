#include "FileSink.h"
#include <iostream>
#include <chrono>
#include <iomanip>
#include <sstream>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "FileSink"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

void FileSink::set_filename(const std::string& filename) {
    if (filename.empty()) {
        LOGE("FileSink: Cannot set empty filename");
        return;
    }

    if (filename_ != filename) {
        LOGI("FileSink: Changing filename from '%s' to '%s'",
             filename_.c_str(), filename.c_str());
        filename_ = filename;
        set_parameter("filename", filename);
        if (is_recording_) {
            reopen();
        }
    }
}

void FileSink::set_mode(FileMode mode) {
    if (mode_ != mode) {
        LOGI("FileSink: Changing mode from %d to %d",
             static_cast<int>(mode_), static_cast<int>(mode));
        mode_ = mode;
        set_parameter("mode", static_cast<int>(mode));
        if (is_recording_) {
            reopen();
        }
    }
}

void FileSink::set_max_file_size(size_t max_size) {
    if (max_file_size_ != max_size) {
        LOGI("FileSink: Setting max file size to %zu bytes", max_size);
        max_file_size_ = max_size;
        set_parameter("max_file_size", static_cast<int>(max_size));
    }
}

void FileSink::start_recording() {
    if (is_recording_) {
        return;
    }

    LOGI("FileSink: Starting recording");
    {
        std::lock_guard<std::recursive_mutex> lock(file_mutex_);
        if (!file_.is_open()) {
            open_file();
        }
    }
    is_recording_ = true;
    set_parameter("is_recording", true);
}

void FileSink::stop_recording() {
    if (!is_recording_) {
        return;
    }

    LOGI("FileSink: Stopping recording");
    is_recording_ = false;
    set_parameter("is_recording", false);

    // Wait for writer queue to drain so all buffered items are written before closing
    while (true) {
        {
            std::unique_lock<std::mutex> lock(buffer_mutex_);
            if (write_queue_.empty()) {
                break;
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }

    std::lock_guard<std::recursive_mutex> lock(file_mutex_);
    if (file_.is_open()) {
        file_.flush();
        file_.close();
        LOGI("FileSink: Closed file on stop recording");
    }
}

void FileSink::flush() {
    std::lock_guard<std::recursive_mutex> lock(file_mutex_);
    if (file_.is_open()) {
        file_.flush();
        LOGV("FileSink: Flushed file");
    }
}

void FileSink::close() {
    std::lock_guard<std::recursive_mutex> lock(file_mutex_);
    if (file_.is_open()) {
        LOGI("FileSink: Closing file '%s'", filename_.c_str());
        file_.close();
    }
}

void FileSink::reopen() {
    LOGI("FileSink: Reopening file");
    std::lock_guard<std::recursive_mutex> lock(file_mutex_);
    close();
    open_file();
}

void FileSink::open_file() {
    std::lock_guard<std::recursive_mutex> lock(file_mutex_);
    std::string actual_filename = filename_;

    if (mode_ == FileMode::TIMESTAMP) {
        actual_filename = generate_timestamp_filename();
    }

    std::ios_base::openmode open_mode = std::ios::binary;
    if (mode_ == FileMode::APPEND) {
        open_mode |= std::ios::app;
        LOGI("FileSink: Opening in APPEND mode");
    } else {
        open_mode |= std::ios::trunc;
        LOGI("FileSink: Opening in TRUNCATE mode");
    }

    file_.open(actual_filename, open_mode);
    if (!file_.is_open()) {
        LOGE("FileSink: Cannot open file: %s", actual_filename.c_str());
        return;
    }

    bytes_written_ = 0;
    LOGI("FileSink: Opened file: %s", actual_filename.c_str());
}

void FileSink::rotate_file() {
    LOGI("FileSink: Rotating file (size limit reached)");

    std::lock_guard<std::recursive_mutex> lock(file_mutex_);
    if (file_.is_open()) {
        file_.close();
    }

    if (mode_ == FileMode::TIMESTAMP) {
        open_file();
    } else {
        size_t dot_pos = filename_.find_last_of('.');
        std::string base_name = filename_.substr(0, dot_pos);
        std::string extension = (dot_pos != std::string::npos) ? filename_.substr(dot_pos) : "";

        int sequence = 1;
        std::string new_filename;
        do {
            std::stringstream ss;
            ss << base_name << "_" << std::setfill('0') << std::setw(3) << sequence << extension;
            new_filename = ss.str();
            sequence++;
        } while (std::ifstream(new_filename).good());

        LOGI("FileSink: Rotating to new file: %s", new_filename.c_str());
        filename_ = new_filename;
        open_file();
    }
}

std::string FileSink::generate_timestamp_filename() const {
    auto now = std::chrono::system_clock::now();
    auto time_t = std::chrono::system_clock::to_time_t(now);
    auto milliseconds = std::chrono::duration_cast<std::chrono::milliseconds>(
            now.time_since_epoch()) % 1000;

    std::stringstream ss;
    ss << std::put_time(std::localtime(&time_t), "%Y%m%d_%H%M%S");
    ss << "_" << std::setfill('0') << std::setw(3) << milliseconds.count();

    size_t dot_pos = filename_.find_last_of('.');
    std::string base_name = filename_.substr(0, dot_pos);
    std::string extension = (dot_pos != std::string::npos) ? filename_.substr(dot_pos) : "";

    return base_name + "_" + ss.str() + extension;
}

void FileSink::process_high_speed(Port* in_port) {
    if (!in_port) {
        LOGV("FileSink: Input port is null");
        return;
    }

    // Check if we need to rotate
    if (is_recording_ && max_file_size_ > 0 && bytes_written_ >= max_file_size_) {
        rotate_file();
    }

    // Process based on data type
    switch (data_type_) {
        case DataType::BYTE:
            process_type_high_speed<uint8_t>(in_port);
            break;
        case DataType::COMPLEX_FLOAT:
            process_type_high_speed<std::complex<float>>(in_port);
            break;
        case DataType::COMPLEX_DOUBLE:
            process_type_high_speed<std::complex<double>>(in_port);
            break;
        case DataType::FLOAT:
            process_type_high_speed<float>(in_port);
            break;
        case DataType::DOUBLE:
            process_type_high_speed<double>(in_port);
            break;
        case DataType::INT32:
            process_type_high_speed<int32_t>(in_port);
            break;
        case DataType::SHORT:
            process_type_high_speed<int16_t>(in_port);
            break;
        default:
            LOGE("FileSink: Unsupported data type: %d", static_cast<int>(data_type_));
            break;
    }
}

void FileSink::writer_loop() {
    LOGI("FileSink: Writer thread started");

    size_t total_written_since_flush = 0;
    const size_t FLUSH_THRESHOLD = 25 * 1024 * 1024; // 25MB

    while (!stop_writer_) {
        std::vector<uint8_t> buffer;

        {
            std::unique_lock<std::mutex> lock(buffer_mutex_);
            writer_cv_.wait_for(lock, std::chrono::milliseconds(100), [this]() {
                return stop_writer_ || !write_queue_.empty();
            });

            if (stop_writer_ && write_queue_.empty()) {
                break;
            }

            if (!write_queue_.empty()) {
                buffer = std::move(write_queue_.front());
                write_queue_.pop();
            }
        }

        if (!buffer.empty()) {
            std::lock_guard<std::recursive_mutex> lock(file_mutex_);
            if (file_.is_open()) {
                // Write data to file
                file_.write(reinterpret_cast<const char*>(buffer.data()), buffer.size());

                if (file_.bad()) {
                    LOGE("FileSink: Write error - file is in bad state");
                    break;
                }

                bytes_written_ += buffer.size();
                total_written_since_flush += buffer.size();

                // Flush periodically
                if (total_written_since_flush > FLUSH_THRESHOLD) {
                    file_.flush();
                    total_written_since_flush = 0;
                    LOGV("FileSink: Flushed after %zu bytes", FLUSH_THRESHOLD);
                }
            }
        }

        // Return buffer to pool if it's large enough
        if (buffer.capacity() >= 262144) { // Only keep properly sized buffers
            buffer.clear();
            std::unique_lock<std::mutex> lock(buffer_mutex_);
            if (free_buffers_.size() < 8) { // Limit pool size
                free_buffers_.push(std::move(buffer));
            }
        }
    }

    // Final flush
    {
        std::lock_guard<std::recursive_mutex> lock(file_mutex_);
        if (file_.is_open()) {
            file_.flush();
            LOGI("FileSink: Final flush completed");
        }
    }

    LOGI("FileSink: Writer thread stopped. Total bytes written: %zu", bytes_written_.load());
}

// Note: The template method process_type_high_speed<T>() is defined in the header.
// Here are explicit instantiations for common types to avoid linker errors.

template void FileSink::process_type_high_speed<uint8_t>(Port*);
template void FileSink::process_type_high_speed<std::complex<float>>(Port*);
template void FileSink::process_type_high_speed<std::complex<double>>(Port*);
template void FileSink::process_type_high_speed<float>(Port*);
template void FileSink::process_type_high_speed<double>(Port*);
template void FileSink::process_type_high_speed<int32_t>(Port*);
template void FileSink::process_type_high_speed<int16_t>(Port*);
