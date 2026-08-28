#ifndef FILE_SINK_H
#define FILE_SINK_H

#include "core/Block.h"  // Fixed path
#include <fstream>
#include <memory>
#include <vector>
#include <atomic>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <android/log.h>

#define LOG_TAG "FileSink"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * @class FileSink
 * @brief Writes data from a port to a file.
 * 
 * Supports multiple file modes (overwrite, append, timestamp) and
 * high-speed writing with a dedicated writer thread and buffer pool.
 */
class FileSink : public Block {
public:
    using sptr = std::shared_ptr<FileSink>;

    enum class FileMode {
        OVERWRITE,  /**< Overwrite existing file */
        APPEND,     /**< Append to existing file */
        TIMESTAMP   /**< Add timestamp to filename */
    };

    /**
     * @brief Create a FileSink
     * @param data_type Data type to write
     * @param filename Output filename
     * @param mode File mode (overwrite, append, timestamp)
     * @param name Block name
     * @param buffer_size Buffer size in items
     */
    FileSink(DataType data_type, const std::string& filename,
             FileMode mode = FileMode::OVERWRITE,
             const std::string& name = "file_sink",
             size_t buffer_size = 1048576)  // Default 1MB buffer
            : Block(name), data_type_(data_type), filename_(filename),
              mode_(mode), bytes_written_(0), max_file_size_(0),
              is_recording_(true), stop_writer_(false) {

        // Add input port with larger buffer for high sample rates
        add_input_port("in", port_config::fixed_type(data_type, buffer_size));

        set_parameter("filename", filename);
        set_parameter("mode", static_cast<int>(mode));
        set_parameter("max_file_size", 0);
        set_parameter("is_recording", true);

        open_file();

        // Pre-allocate buffers for high-speed writing
        for (size_t i = 0; i < 4; ++i) {
            free_buffers_.emplace(262144); // 256KB buffers
        }

        writer_thread_ = std::thread(&FileSink::writer_loop, this);
        LOGI("FileSink created: %s", filename.c_str());
    }

    /**
     * @brief Factory method
     */
    static sptr make(DataType data_type, const std::string& filename,
                     FileMode mode = FileMode::OVERWRITE,
                     const std::string& name = "file_sink",
                     size_t buffer_size = 1048576) {
        return std::make_shared<FileSink>(data_type, filename, mode, name, buffer_size);
    }

    ~FileSink() override {
        LOGI("FileSink destroying...");
        is_recording_ = false;
        stop_writer_ = true;
        writer_cv_.notify_all();
        if (writer_thread_.joinable()) {
            writer_thread_.join();
        }

        std::lock_guard<std::recursive_mutex> lock(file_mutex_);
        if (file_.is_open()) {
            file_.close();
        }
        LOGI("FileSink destroyed");
    }

    /**
     * @brief Process incoming data and write to file
     */
    void work() override {
        auto* in_port = get_input_port("in");
        if (!in_port) return;

        process_high_speed(in_port);

        if (is_recording_ && max_file_size_ > 0 && bytes_written_ >= max_file_size_) {
            rotate_file();
        }
    }

    int64_t nativeCreateBlock(const std::string& name) override {
        return reinterpret_cast<long>(this);
    }

    // Configuration

    /**
     * @brief Set the output filename
     */
    void set_filename(const std::string& filename);

    /**
     * @brief Set the file mode
     */
    void set_mode(FileMode mode);

    /**
     * @brief Set maximum file size before rotation
     */
    void set_max_file_size(size_t max_size);

    // Recording Control

    /**
     * @brief Start recording data to file
     */
    void start_recording();

    /**
     * @brief Stop recording data and close file
     */
    void stop_recording();

    /**
     * @brief Check if recording is currently active
     */
    bool is_recording() const { return is_recording_; }

    // File Operations

    /**
     * @brief Flush the file buffer
     */
    void flush();

    /**
     * @brief Close the file
     */
    void close();

    /**
     * @brief Reopen the file
     */
    void reopen();

    // Status
    size_t get_bytes_written() const { return bytes_written_; }
    bool is_open() const {
        std::lock_guard<std::recursive_mutex> lock(file_mutex_);
        return file_.is_open();
    }
    std::string get_filename() const { return filename_; }

private:
    // Member Variables
    DataType data_type_;
    std::string filename_;
    FileMode mode_;
    std::atomic<size_t> bytes_written_;
    size_t max_file_size_;

    std::atomic<bool> is_recording_;
    mutable std::recursive_mutex file_mutex_;
    std::ofstream file_;

    // High-speed writing system
    std::thread writer_thread_;
    std::mutex buffer_mutex_;
    std::condition_variable writer_cv_;
    std::queue<std::vector<uint8_t>> write_queue_;
    std::queue<std::vector<uint8_t>> free_buffers_;
    std::atomic<bool> stop_writer_;

    // Private Methods
    void open_file();
    void rotate_file();
    std::string generate_timestamp_filename() const;
    void process_high_speed(Port* in_port);
    void writer_loop();

    /**
     * @brief Template method for type-safe processing
     */
    template<typename T>
    void process_type_high_speed(Port* in_port) {
        const size_t available_items = in_port->items_available<T>();
        if (available_items == 0) return;

        if (!is_recording_) {
            // Not recording: consume (skip) data so ring buffer doesn't fill up and block other blocks
            auto* buf = in_port->get_buffer();
            if (buf) {
                buf->skip(available_items);
            }
            return;
        }

        // Get a buffer from the free pool
        std::vector<uint8_t> buffer;
        {
            std::unique_lock<std::mutex> lock(buffer_mutex_);
            if (!free_buffers_.empty()) {
                buffer = std::move(free_buffers_.front());
                free_buffers_.pop();
            }
        }

        // If no free buffer, allocate new one
        if (buffer.empty()) {
            buffer.resize(262144); // 256KB
        }

        // Calculate how much to read
        size_t max_items = buffer.size() / sizeof(T);
        size_t to_read = std::min(available_items, max_items);

        if (to_read > 0) {
            // Read directly into buffer
            if (in_port->read(reinterpret_cast<T*>(buffer.data()), to_read)) {
                buffer.resize(to_read * sizeof(T)); // Trim to actual size

                // Send to writer thread
                {
                    std::unique_lock<std::mutex> lock(buffer_mutex_);
                    write_queue_.push(std::move(buffer));
                }
                writer_cv_.notify_one();
            }
        }
    }
};

#endif // FILE_SINK_H