#ifndef JAVA_SINK_H
#define JAVA_SINK_H

#include "core/Block.h"
#include <mutex>

/**
 * @class JavaSink
 * @brief High-performance sink block that provides data to Java.
 *
 * This block collects processed DSP data and stores it in an internal
 * interop buffer which Java can read from using direct ByteBuffers.
 */
class JavaSink : public Block {
public:
    /**
     * @brief Create a JavaSink
     * @param type Data type to receive from input port
     * @param buffer_capacity Capacity of internal buffer in items
     * @param name Block name
     */
    JavaSink(DataType type, size_t buffer_capacity = 65536, const std::string& name = "java_sink");

    ~JavaSink() override;

    /**
     * @brief Pull data from the block into a Java-owned buffer.
     * @param data Destination pointer (usually from a direct ByteBuffer)
     * @param max_count Maximum items to pull
     * @return Number of items actually pulled
     */
    size_t pull(void* data, size_t max_count);

    /**
     * @brief Get number of items available for Java to pull.
     */
    size_t read_available() const;

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

#endif // JAVA_SINK_H
