#include "FileSource.h"
#include <android/log.h>
#include <algorithm>

#define LOG_TAG "FileSource"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

FileSource::FileSource(DataType data_type, const std::string& filename,
                       bool repeat, const std::string& name)
        : Block(name),
          data_type_(data_type),
          filename_(filename),
          repeat_(repeat),
          file_size_(0),
          position_(0),
          eof_(false) {

    item_size_ = get_type_size(data_type);

    // Add output port
    add_output_port("out", port_config::fixed_type(data_type));

    set_parameter("filename", filename);
    set_parameter("repeat", repeat);

    open_file();
}

FileSource::~FileSource() {
    close_file();
}

void FileSource::open_file() {
    close_file();

    file_.open(filename_, std::ios::binary | std::ios::ate);
    if (!file_.is_open()) {
        LOGE("FileSource: Failed to open file '%s'", filename_.c_str());
        return;
    }

    file_size_ = static_cast<size_t>(file_.tellg());
    file_.seekg(0, std::ios::beg);
    position_ = 0;
    eof_ = false;

    LOGI("FileSource: Opened file '%s', size: %zu bytes", filename_.c_str(), file_size_);
}

void FileSource::close_file() {
    if (file_.is_open()) {
        file_.close();
    }
}

void FileSource::set_filename(const std::string& filename) {
    if (filename_ != filename) {
        filename_ = filename;
        set_parameter("filename", filename);
        open_file();
    }
}

void FileSource::set_repeat(bool repeat) {
    repeat_ = repeat;
    set_parameter("repeat", repeat);
}

void FileSource::seek(size_t position) {
    if (file_.is_open()) {
        file_.clear(); // Clear EOF flag if set
        file_.seekg(position, std::ios::beg);
        position_ = static_cast<size_t>(file_.tellg());
        eof_ = false;
    }
}

bool FileSource::is_ready() {
    // If not repeating and reached EOF, we are not ready
    return is_active() && file_.is_open() && (!eof_ || repeat_);
}

void FileSource::work() {
    if (!is_active() || !file_.is_open()) return;

    if (eof_ && !repeat_) return;

    if (eof_ && repeat_) {
        seek(0);
    }

    // Process based on type
    switch (data_type_) {
        case DataType::BYTE: process_type<uint8_t>(); break;
        case DataType::COMPLEX_FLOAT: process_type<std::complex<float>>(); break;
        case DataType::COMPLEX_DOUBLE: process_type<std::complex<double>>(); break;
        case DataType::FLOAT: process_type<float>(); break;
        case DataType::DOUBLE: process_type<double>(); break;
        case DataType::INT32: process_type<int32_t>(); break;
        case DataType::SHORT: process_type<int16_t>(); break;
        default: break;
    }
}

template<typename T>
void FileSource::process_type() {
    auto* out_port = get_output_port("out");
    if (!out_port) return;

    // Check how much space we have in output ports
    // Since we handle fan-out, we should check if all connected ports have space.
    // However, the port->write will return false if any fails.

    // Read a chunk (e.g., 1024 items)
    constexpr size_t CHUNK_SIZE = 1024;
    std::vector<T> buffer(CHUNK_SIZE);

    file_.read(reinterpret_cast<char*>(buffer.data()), CHUNK_SIZE * sizeof(T));
    std::streamsize bytes_read = file_.gcount();
    auto items_read = static_cast<size_t>(bytes_read / sizeof(T));

    if (items_read > 0) {
        if (!out_port->write(buffer.data(), items_read)) {
            // If write fails (no space in some output port), we should step back
            file_.seekg(-static_cast<std::streamoff>(bytes_read), std::ios::cur);
        } else {
            position_ = static_cast<size_t>(file_.tellg());
        }
    }

    if (file_.eof()) {
        eof_ = true;
        LOGI("FileSource: Reached EOF");
    }
}
