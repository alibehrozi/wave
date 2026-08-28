#ifndef FLOWGRAPH_H
#define FLOWGRAPH_H

#include "Block.h"
#include <vector>
#include <memory>
#include <thread>
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <unordered_map>
#include <functional>
#include <set>

/**
 * @class Flowgraph
 * @brief Manages a collection of connected DSP blocks as a single processing flowgraph.
 *
 * A Flowgraph is a container that:
 * - Manages the lifecycle of multiple Block objects
 * - Handles connections between blocks
 * - Provides a worker thread for sequential processing
 * - Supports dynamic addition/removal of blocks
 *
 * @section Execution Model
 * The Flowgraph runs all blocks in a single thread, processing them
 * sequentially in the order they were added. This is the simplest
 * execution model for DSP processing.
 *
 * @section Usage Example
 * @code
 * // Create a flowgraph
 * auto flowgraph = Flowgraph::make("audio_chain");
 *
 * // Create blocks
 * auto source = std::make_shared<AudioSource>("mic");
 * auto filter = std::make_shared<LowPassFilter>("filter");
 * auto sink = std::make_shared<AudioSink>("speaker");
 *
 * // Add blocks to flowgraph
 * flowgraph->add_block(source);
 * flowgraph->add_block(filter);
 * flowgraph->add_block(sink);
 *
 * // Connect blocks
 * flowgraph->connect(source, "out", filter, "in");
 * flowgraph->connect(filter, "out", sink, "in");
 *
 * // Start processing
 * flowgraph->start();
 * flowgraph->wait(); // Wait for completion
 * @endcode
 *
 * @section Thread Safety
 * All public methods are thread-safe using a mutex for synchronization.
 * The worker thread runs independently and can be started/stopped safely.
 */
class Flowgraph {
public:
    using sptr = std::shared_ptr<Flowgraph>;

    /**
     * @brief Construct a new Flowgraph
     *
     * @param name Unique name for this flowgraph
     */
    Flowgraph(const std::string& name = "flowgraph");

    /**
     * @brief Destroy the Flowgraph
     *
     * Automatically stops the flowgraph and releases all resources.
     */
    ~Flowgraph();

    /**
     * @brief Connect two blocks using specified port names
     *
     * @param src_block Source block (must not be null)
     * @param src_port Source port name (must be a valid output port)
     * @param dst_block Destination block (must not be null)
     * @param dst_port Destination port name (must be a valid input port)
     * @return true if connection was successful
     * @throws std::invalid_argument if any parameter is invalid
     * @throws std::runtime_error if connection fails
     */
    bool connect(const std::shared_ptr<Block>& src_block, const std::string& src_port,
                 const std::shared_ptr<Block>& dst_block, const std::string& dst_port);

    /**
     * @brief Connect two blocks using port indices
     *
     * @param src_block Source block (must not be null)
     * @param src_port_index Source port index (must be valid)
     * @param dst_block Destination block (must not be null)
     * @param dst_port_index Destination port index (must be valid)
     * @return true if connection was successful
     */
    bool connect(const std::shared_ptr<Block>& src_block, size_t src_port_index,
                 const std::shared_ptr<Block>& dst_block, size_t dst_port_index);

    /**
     * @brief Connect two blocks using default ports ("out" and "in")
     *
     * Convenience method that assumes blocks have "out" and "in" ports.
     *
     * @param src_block Source block
     * @param dst_block Destination block
     * @return true if connection was successful
     */
    bool connect(const std::shared_ptr<Block>& src_block,
                 const std::shared_ptr<Block>& dst_block);

    /**
     * @brief Disconnect two blocks using specified port names
     *
     * @param src_block Source block
     * @param src_port Source port name
     * @param dst_block Destination block
     * @param dst_port Destination port name
     */
    void disconnect(const std::shared_ptr<Block>& src_block, const std::string& src_port,
                    const std::shared_ptr<Block>& dst_block, const std::string& dst_port);

    /**
     * @brief Disconnect two blocks using default ports ("out" and "in")
     *
     * @param src_block Source block
     * @param dst_block Destination block
     */
    void disconnect(const std::shared_ptr<Block>& src_block,
                    const std::shared_ptr<Block>& dst_block);

    /**
     * @brief Add a block to the flowgraph
     *
     * The block will be managed by the flowgraph and stopped when
     * the flowgraph stops.
     *
     * @param block Block to add (must not be null)
     * @throws std::invalid_argument if block is null
     */
    void add_block(const std::shared_ptr<Block>& block);

    /**
     * @brief Remove a block from the flowgraph
     *
     * The block is disconnected from all other blocks and stopped.
     *
     * @param block Block to remove
     */
    void remove_block(const std::shared_ptr<Block>& block);

    /**
     * @brief Remove a block by name
     *
     * @param name Name of the block to remove
     */
    void remove_block(const std::string& name);

    /**
     * @brief Get a block by name
     *
     * @param name Name of the block to find
     * @return Shared pointer to the block, or nullptr if not found
     */
    std::shared_ptr<Block> get_block(const std::string& name) const;

    /**
     * @brief Get all blocks in the flowgraph
     *
     * @return Vector of all blocks
     */
    std::vector<std::shared_ptr<Block>> get_blocks() const;

    /**
     * @brief Start the flowgraph (non-blocking)
     *
     * Starts the worker thread that processes all blocks sequentially.
     * This method returns immediately.
     *
     * @throws std::runtime_error if flowgraph is empty
     */
    void start();

    /**
     * @brief Stop the flowgraph
     *
     * Stops the worker thread and all blocks.
     * This method blocks until the worker thread has exited.
     */
    void stop();

    /**
     * @brief Wait for the flowgraph to complete (blocking)
     *
     * Blocks until the flowgraph stops or is stopped.
     * The flowgraph must be started before calling this method.
     */
    void wait();

    /**
     * @brief Run the flowgraph and wait for completion (blocking)
     *
     * Starts the flowgraph and blocks until it completes.
     * Equivalent to calling start() followed by wait().
     */
    void run();

    /**
     * @brief Check if the flowgraph is running
     * @return true if running
     */
    bool is_running() const { return running_.load(std::memory_order_acquire); }

    /**
     * @brief Get the flowgraph name
     * @return Flowgraph name
     */
    const std::string& get_name() const { return name_; }

    /**
     * @brief Get the number of blocks in the flowgraph
     * @return Block count
     */
    size_t get_block_count() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return blocks_.size();
    }

    /**
     * @brief Get the number of connections in the flowgraph
     * @return Connection count
     */
    size_t get_connection_count() const;

    /**
     * @brief Check if two blocks are connected
     *
     * @param src_block Source block
     * @param src_port Source port name
     * @param dst_block Destination block
     * @param dst_port Destination port name
     * @return true if connected
     */
    bool are_connected(const std::shared_ptr<Block>& src_block, const std::string& src_port,
                       const std::shared_ptr<Block>& dst_block, const std::string& dst_port) const;

    /**
     * @brief Create a new Flowgraph
     *
     * @param name Flowgraph name
     * @return Shared pointer to the new Flowgraph
     */
    static sptr make(const std::string& name = "flowgraph") {
        return std::make_shared<Flowgraph>(name);
    }

private:

    /**
     * @struct Connection
     * @brief Represents a connection between two blocks
     *
     * Uses weak_ptr to avoid circular references and allow
     * automatic cleanup when blocks are destroyed.
     */
    struct Connection {
        std::weak_ptr<Block> src_block; /**< Source block (weak ref) */
        std::string src_port;           /**< Source port name */
        std::weak_ptr<Block> dst_block; /**< Destination block (weak ref) */
        std::string dst_port;           /**< Destination port name */
    };

    /**
     * @brief Worker thread function
     *
     * Processes all blocks in a loop until stop is requested.
     */
    void worker_thread();

    /**
     * @brief Add a block to internal structures
     *
     * This method assumes the mutex is already locked.
     *
     * @param block Block to add
     */
    void add_block_internal(const std::shared_ptr<Block>& block);

    /**
     * @brief Remove a block from internal structures
     *
     * This method assumes the mutex is already locked.
     *
     * @param block Block to remove
     */
    void remove_block_internal(const std::shared_ptr<Block>& block);

    /**
     * @brief Clean up blocks that have no connections
     *
     * Removes blocks that are not connected to anything if
     * auto_cleanup_ is enabled.
     */
    void cleanup_disconnected_blocks();

    /**
     * @brief Validate if a connection is possible
     *
     * Checks that ports exist, are compatible, and not already connected.
     *
     * @param src_block Source block
     * @param src_port Source port name
     * @param dst_block Destination block
     * @param dst_port Destination port name
     * @return true if connection is valid
     */
    bool validate_connection(const std::shared_ptr<Block>& src_block,
                             const std::string& src_port,
                             const std::shared_ptr<Block>& dst_block,
                             const std::string& dst_port) const;

    // Private Member Variables

    std::string name_;                              /**< Flowgraph name */
    std::vector<std::shared_ptr<Block>> blocks_;   /**< All blocks in the flowgraph */
    std::unordered_map<std::string, std::shared_ptr<Block>> block_map_; /**< Block lookup by name */
    std::vector<Connection> connections_;          /**< All connections */
    mutable std::mutex mutex_;                     /**< Mutex for thread safety */

    std::atomic<bool> running_{false};             /**< Flowgraph running state */
    std::atomic<bool> stop_requested_{false};      /**< Stop requested flag */
    std::atomic<bool> auto_cleanup_{true};         /**< Auto cleanup enabled */

    std::unique_ptr<std::thread> worker_thread_;   /**< Worker thread */
    std::condition_variable cv_;                   /**< Condition variable for worker */

    size_t iterations_ = 0;                        /**< Iteration counter */
    std::chrono::steady_clock::time_point start_time_; /**< Start time for monitoring */
};

#endif // FLOWGRAPH_H