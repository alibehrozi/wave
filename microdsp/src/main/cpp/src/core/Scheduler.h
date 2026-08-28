#ifndef SCHEDULER_H
#define SCHEDULER_H

#include "Block.h"
#include <vector>
#include <memory>
#include <thread>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <functional>
#include <future>
#include <chrono>
#include <unordered_map>
#include <android/log.h>


/**
 * @class Scheduler
 * @brief Manages the concurrent execution of DSP blocks across multiple threads.
 *
 * The Scheduler provides a multi-threaded execution environment for Blocks.
 * It supports different scheduling strategies to optimize for latency, throughput,
 * or real-time constraints.
 */
class Scheduler {
public:
    /**
     * @enum Strategy
     * @brief Specifies how tasks are scheduled for execution
     */
    enum class Strategy {
        ROUND_ROBIN, /**< Each block gets equal processing time in sequence */
        PRIORITY,    /**< Higher priority blocks execute first */
        DATAFLOW,    /**< Blocks execute when they have enough input data and output space */
        REAL_TIME    /**< Blocks execute based on strict deadlines */
    };

    /**
     * @struct Task
     * @brief Internal representation of a scheduled block execution
     */
    struct Task {
        std::shared_ptr<Block> block;                    /**< The block to execute */
        uint32_t priority{0};                             /**< Task priority */
        std::chrono::steady_clock::time_point deadline;   /**< Execution deadline */
        std::chrono::microseconds period{0};              /**< Period for periodic tasks */
        uint64_t task_id{0};                              /**< Unique task ID */

        bool operator<(const Task& other) const {
            return priority < other.priority;
        }
    };

    /**
     * @brief Get the singleton instance of the Scheduler
     * @return Reference to the Scheduler instance
     */
    static Scheduler& get_instance();

    /**
     * @brief Start the scheduler with a pool of worker threads
     * @param thread_count Number of worker threads (0 = auto-detect CPU cores)
     */
    void start(size_t thread_count = 0);

    /**
     * Stop the scheduler gracefully
     */
    void stop();

    /**
     * Wait for all tasks to complete
     */
    void wait();

    /**
     * Add a block to the scheduler
     * @param block Block to schedule
     * @param priority Priority (higher = more important)
     */
    void add_block(std::shared_ptr<Block> block, uint32_t priority = 0);

    /**
     * Remove a block from the scheduler
     * @param block Block to remove
     */
    void remove_block(std::shared_ptr<Block> block);

    /**
     * Set the priority of a block
     * @param block Block to update
     * @param priority New priority
     */
    void set_block_priority(std::shared_ptr<Block> block, uint32_t priority);

    /**
     * Check if a block is scheduled
     * @param block Block to check
     * @return true if scheduled
     */
    bool has_block(std::shared_ptr<Block> block) const;

    /**
     * Set the scheduling strategy
     * @param strategy Strategy to use
     */
    void set_strategy(Strategy strategy);

    /**
     * Enable/disable CPU affinity
     * @param use_affinity true to enable
     */
    void set_affinity(bool use_affinity);

    /**
     * Set deadline for a block (real-time scheduling)
     * @param block Block to set deadline for
     * @param deadline Deadline in microseconds
     */
    void set_deadline(std::shared_ptr<Block> block, std::chrono::microseconds deadline);

    /**
     * Set period for a block (periodic execution)
     * @param block Block to set period for
     * @param period Period in microseconds
     */
    void set_period(std::shared_ptr<Block> block, std::chrono::microseconds period);

    /**
     * Get number of active worker threads
     */
    size_t get_active_threads() const;

    /**
     * Get number of pending tasks
     */
    size_t get_pending_tasks() const;

    /**
     * Get thread utilization percentage
     */
    double get_thread_utilization() const;

    /**
     * Get total tasks processed
     */
    size_t get_total_tasks_processed() const {
        return total_tasks_processed_.load(std::memory_order_acquire);
    }

    /**
     * Get the current scheduling strategy
     */
    Strategy get_strategy() const { return strategy_; }

private:
    Scheduler();
    ~Scheduler();

    // Non-copyable
    Scheduler(const Scheduler&) = delete;
    Scheduler& operator=(const Scheduler&) = delete;

    /**
     * Worker thread function
     * @param thread_id Thread identifier
     */
    void worker_thread(size_t thread_id);

    /**
     * Get the next task to execute
     * @return Next task
     */
    Task get_next_task();

    /**
     * Notify workers that tasks are available
     */
    void schedule_tasks();

    std::vector<std::thread> worker_threads_;
    std::atomic<bool> running_{false};
    std::atomic<bool> stop_requested_{false};

    mutable std::mutex mutex_;
    std::condition_variable cv_;
    std::condition_variable worker_cv_;

    // Task queues
    std::vector<Task> ready_queue_;
    std::vector<std::shared_ptr<Block>> blocks_;
    std::unordered_map<std::shared_ptr<Block>, Task> task_map_;
    std::atomic<uint64_t> next_task_id_{0};

    // Scheduling configuration
    Strategy strategy_{Strategy::ROUND_ROBIN};
    bool use_affinity_{false};
    size_t next_thread_{0};

    // Statistics
    std::atomic<size_t> active_threads_{0};
    std::atomic<size_t> total_tasks_processed_{0};
    std::chrono::steady_clock::time_point start_time_;
};

#endif // SCHEDULER_H