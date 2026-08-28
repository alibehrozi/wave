#ifndef JAVA_SOURCE_H
#define JAVA_SOURCE_H

#include "core/Block.h"
#include <mutex>

/**
 * @class JavaSource
 * @brief High-performance source block that receives data from Java.
 *
 * This block provides an interop buffer that can be filled from Java
 * using direct ByteBuffers. It then pushes this data into the DSP pipeline.
 */
class JavaSource : public Block {
public:
    /**
     * @brief Create a JavaSource
     * @param type Data type to output
     * @param buffer_capacity Capacity of the internal interop buffer in items
     * @param name Block name
     */
    JavaSource(DataType type, size_t buffer_capacity = 65536, const std::string& name = "java_source");

    ~JavaSource() override;

    /**
     * @brief Push data from Java into the block's interop buffer.
     * @param data Pointer to the data (usually from a direct ByteBuffer)
     * @param count Number of items to push
     * @return Number of items actually pushed
     */
    size_t push(const void* data, size_t count);

    /**
     * @brief Get the number of items Java can currently push.
     */
    size_t write_available() const;

    void work() override;
    bool is_ready() override;
    void reset() override;

    int64_t nativeCreateBlock(const std::string& name) override {
        return reinterpret_cast<int64_t>(this);
    }

private:
    DataType type_;
    std::unique_ptr<RingBuffer> interop_buffer_;
    size_t item_size_;
    std::vector<uint8_t> work_buffer_;
};

#endif // JAVA_SOURCE_H
