#ifndef FILE_SOURCE_H
#define FILE_SOURCE_H

#include "core/Block.h"
#include <fstream>
#include <memory>
#include <vector>
#include <string>

/**
 * @class FileSource
 * @brief Reads data from a file and outputs it to the DSP pipeline.
 *
 * Supports reading multiple data types, seeking, and optional looping.
 */
class FileSource : public Block {
public:
    using sptr = std::shared_ptr<FileSource>;

    /**
     * @brief Create a FileSource
     * @param data_type Data type to read from file
     * @param filename Input filename
     * @param repeat Whether to loop the file when EOF is reached
     * @param name Block name
     */
    FileSource(DataType data_type, const std::string& filename,
               bool repeat = false,
               const std::string& name = "file_source");

    /**
     * @brief Factory method
     */
    static sptr make(DataType data_type, const std::string& filename,
                     bool repeat = false,
                     const std::string& name = "file_source") {
        return std::make_shared<FileSource>(data_type, filename, repeat, name);
    }

    ~FileSource() override;

    /**
     * @brief Read data from file and write to output port
     */
    void work() override;

    /**
     * @brief Check if block is ready to perform work
     */
    bool is_ready() override;

    /**
     * @brief Create native block for JNI
     * @param name Block name
     * @return Native handle
     */
    int64_t nativeCreateBlock(const std::string& name) override {
        return reinterpret_cast<long>(this);
    }

    // Configuration
    void set_filename(const std::string& filename);
    void set_repeat(bool repeat);
    void seek(size_t position);

    // Status
    size_t get_file_size() const { return file_size_; }
    size_t get_position() const { return position_; }
    bool is_open() const { return file_.is_open(); }
    bool is_eof() const { return eof_; }

private:
    DataType data_type_;
    std::string filename_;
    bool repeat_;

    std::ifstream file_;
    size_t file_size_;
    size_t position_;
    bool eof_;

    size_t item_size_;

    void open_file();
    void close_file();

    template<typename T>
    void process_type();
};

#endif // FILE_SOURCE_H
