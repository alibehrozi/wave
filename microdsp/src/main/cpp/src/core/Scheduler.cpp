#include "Scheduler.h"
#include <algorithm>
#include <iostream>
#include <thread>
#include <chrono>
#include <cerrno>
#include <sched.h>
#include <sys/resource.h>
#include <android/log.h>

#define LOG_TAG "Scheduler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

Scheduler::Scheduler() {
    LOGI("Scheduler created");
}

Scheduler::~Scheduler() {
    LOGI("Scheduler destroyed");
    stop();
}

Scheduler& Scheduler::get_instance() {
    static Scheduler instance;
    return instance;
}

void Scheduler::start(size_t thread_count) {
    if (running_.exchange(true, std::memory_order_acq_rel)) {
        LOGI("Scheduler already running");
        return;
    }

    stop_requested_.store(false, std::memory_order_release);
    start_time_ = std::chrono::steady_clock::now();
    total_tasks_processed_.store(0, std::memory_order_release);

    // Auto-detect thread count if not specified
    if (thread_count == 0) {
        thread_count = std::thread::hardware_concurrency();
        if (thread_count == 0) {
            thread_count = 4; // Fallback
        }
        LOGI("Auto-detected %zu CPU cores", thread_count);
    }

    // Create worker threads
    worker_threads_.reserve(thread_count);
    for (size_t i = 0; i < thread_count; ++i) {
        worker_threads_.emplace_back(&Scheduler::worker_thread, this, i);
    }

    active_threads_.store(thread_count, std::memory_order_release);
    LOGI("Scheduler started with %zu threads", thread_count);
}

void Scheduler::stop() {
    if (!running_.exchange(false, std::memory_order_acq_rel)) {
        LOGI("Scheduler already stopped");
        return;
    }

    LOGI("Stopping scheduler...");
    stop_requested_.store(true, std::memory_order_release);

    // Wake up all workers
    cv_.notify_all();
    worker_cv_.notify_all();

    // Wait for all threads to finish
    for (auto& thread : worker_threads_) {
        if (thread.joinable()) {
            thread.join();
        }
    }
    worker_threads_.clear();

    auto end_time = std::chrono::steady_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(
            end_time - start_time_);

    LOGI("Scheduler stopped. Processed %zu tasks in %lld ms",
         total_tasks_processed_.load(), duration.count());
}

void Scheduler::wait() {
    std::unique_lock<std::mutex> lock(mutex_);
    cv_.wait(lock, [this]() {
        return ready_queue_.empty() && !running_.load(std::memory_order_acquire);
    });
}

void Scheduler::add_block(std::shared_ptr<Block> block, uint32_t priority) {
    if (!block) {
        LOGE("Cannot add null block");
        return;
    }

    std::lock_guard<std::mutex> lock(mutex_);

    // Check if block already exists
    if (task_map_.find(block) != task_map_.end()) {
        LOGV("Block '%s' already in scheduler", block->get_name().c_str());
        return;
    }

    Task task;
    task.block = block;
    task.priority = priority;
    task.deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(100);
    task.task_id = next_task_id_.fetch_add(1, std::memory_order_relaxed);

    blocks_.push_back(block);
    task_map_[block] = task;
    ready_queue_.push_back(task);

    // Sort by priority if using priority strategy
    if (strategy_ == Strategy::PRIORITY) {
        std::push_heap(ready_queue_.begin(), ready_queue_.end());
    }

    LOGI("Added block '%s' to scheduler with priority %u (task_id: %lu)",
         block->get_name().c_str(), priority, task.task_id);

    worker_cv_.notify_one();
}

void Scheduler::remove_block(std::shared_ptr<Block> block) {
    if (!block) {
        LOGE("Cannot remove null block");
        return;
    }

    std::lock_guard<std::mutex> lock(mutex_);

    // Remove from blocks list
    auto it = std::find(blocks_.begin(), blocks_.end(), block);
    if (it != blocks_.end()) {
        blocks_.erase(it);
    }

    // Remove from task map
    auto task_it = task_map_.find(block);
    if (task_it != task_map_.end()) {
        LOGI("Removed block '%s' from scheduler", block->get_name().c_str());
        task_map_.erase(task_it);
    }

    // Remove from ready queue
    ready_queue_.erase(
            std::remove_if(ready_queue_.begin(), ready_queue_.end(),
                           [&](const Task& task) {
                               return task.block == block;
                           }),
            ready_queue_.end()
    );

    if (strategy_ == Strategy::PRIORITY) {
        std::make_heap(ready_queue_.begin(), ready_queue_.end());
    }
}

void Scheduler::set_block_priority(std::shared_ptr<Block> block, uint32_t priority) {
    if (!block) return;

    std::lock_guard<std::mutex> lock(mutex_);

    auto it = task_map_.find(block);
    if (it != task_map_.end()) {
        it->second.priority = priority;
        LOGI("Block '%s' priority set to %u", block->get_name().c_str(), priority);

        // Re-sort if using priority strategy
        if (strategy_ == Strategy::PRIORITY) {
            std::make_heap(ready_queue_.begin(), ready_queue_.end());
        }
    } else {
        LOGV("Block '%s' not found in scheduler", block->get_name().c_str());
    }
}

bool Scheduler::has_block(std::shared_ptr<Block> block) const {
    if (!block) return false;

    std::lock_guard<std::mutex> lock(mutex_);
    return task_map_.find(block) != task_map_.end();
}

void Scheduler::set_strategy(Strategy strategy) {
    std::lock_guard<std::mutex> lock(mutex_);

    Strategy old_strategy = strategy_;
    strategy_ = strategy;

    LOGI("Scheduling strategy changed from %d to %d",
         static_cast<int>(old_strategy), static_cast<int>(strategy));

    if (strategy == Strategy::PRIORITY && !ready_queue_.empty()) {
        std::make_heap(ready_queue_.begin(), ready_queue_.end());
    }
}

void Scheduler::set_affinity(bool use_affinity) {
    use_affinity_ = use_affinity;
    LOGI("CPU affinity %s", use_affinity ? "enabled" : "disabled");
}

void Scheduler::set_deadline(std::shared_ptr<Block> block, std::chrono::microseconds deadline) {
    if (!block) return;

    std::lock_guard<std::mutex> lock(mutex_);

    auto it = task_map_.find(block);
    if (it != task_map_.end()) {
        it->second.deadline = std::chrono::steady_clock::now() + deadline;
        LOGI("Block '%s' deadline set to %lld us",
             block->get_name().c_str(), deadline.count());
    }
}

void Scheduler::set_period(std::shared_ptr<Block> block, std::chrono::microseconds period) {
    if (!block) return;

    std::lock_guard<std::mutex> lock(mutex_);

    auto it = task_map_.find(block);
    if (it != task_map_.end()) {
        it->second.period = period;
        LOGI("Block '%s' period set to %lld us",
             block->get_name().c_str(), period.count());
    }
}

size_t Scheduler::get_active_threads() const {
    return active_threads_.load(std::memory_order_acquire);
}

size_t Scheduler::get_pending_tasks() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return ready_queue_.size();
}

double Scheduler::get_thread_utilization() const {
    auto now = std::chrono::steady_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(
            now - start_time_);

    if (duration.count() == 0) return 0.0;

    // Simple utilization calculation
    size_t processed = total_tasks_processed_.load(std::memory_order_acquire);
    size_t active = active_threads_.load(std::memory_order_acquire);

    // Each task takes approximately 1ms to process
    double total_work = processed * 1000.0; // microseconds
    double total_time = static_cast<double>(duration.count()) * active;

    if (total_time == 0) return 0.0;

    return std::min(1.0, total_work / total_time);
}

void Scheduler::worker_thread(size_t thread_id) {
    LOGI("Worker thread %zu started", thread_id);

    // Request elevated thread priority for DSP work.
    // Note: -19 requires CAP_SYS_NICE (root). On most Android apps this will
    // silently fail. We use a moderate value and log a warning on failure.
    if (setpriority(PRIO_PROCESS, 0, -10) != 0) {
        LOGI("Worker thread %zu: setpriority failed (errno=%d) — running at default priority",
             thread_id, errno);
    }

    // Set thread affinity if requested
    if (use_affinity_) {
#ifdef __ANDROID__
        // Android-specific CPU affinity
        cpu_set_t cpuset;
        CPU_ZERO(&cpuset);
        CPU_SET(thread_id % std::thread::hardware_concurrency(), &cpuset);
        sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
#elif defined(__linux__)
        // Desktop Linux
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(thread_id % std::thread::hardware_concurrency(), &cpuset);
    pthread_setaffinity_np(pthread_self(), sizeof(cpu_set_t), &cpuset);
#endif
    }

    while (!stop_requested_.load(std::memory_order_acquire)) {
        Task task = get_next_task();

        if (!task.block) {
            // No task available, wait for notification
            std::unique_lock<std::mutex> lock(mutex_);
            worker_cv_.wait_for(lock, std::chrono::milliseconds(10));
            continue;
        }

        // Execute the block's work function
        try {
            if (task.block->is_active()) {
                auto start = std::chrono::steady_clock::now();
                task.block->work();
                auto end = std::chrono::steady_clock::now();

                auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(
                        end - start);

                if (elapsed.count() > 1000) { // > 1ms
                    LOGV("Block '%s' work took %lld us",
                         task.block->get_name().c_str(), elapsed.count());
                }

                total_tasks_processed_.fetch_add(1, std::memory_order_relaxed);
            }
        } catch (const std::exception& e) {
            LOGE("Error in scheduler worker thread %zu executing block '%s': %s",
                 thread_id, task.block->get_name().c_str(), e.what());
        } catch (...) {
            LOGE("Unknown error in scheduler worker thread %zu executing block '%s'",
                 thread_id, task.block->get_name().c_str());
        }

        // Re-add task to ready queue if block is still active
        if (task.block && task.block->is_active()) {
            std::lock_guard<std::mutex> lock(mutex_);

            // Check if block still exists
            if (task_map_.find(task.block) != task_map_.end()) {
                // Update deadline for real-time scheduling
                if (strategy_ == Strategy::REAL_TIME && task.period.count() > 0) {
                    task.deadline = std::chrono::steady_clock::now() + task.period;
                }

                ready_queue_.push_back(task);

                if (strategy_ == Strategy::PRIORITY) {
                    std::push_heap(ready_queue_.begin(), ready_queue_.end());
                }

                worker_cv_.notify_one();
            }
        }
    }

    active_threads_.fetch_sub(1, std::memory_order_release);
    LOGI("Worker thread %zu stopped", thread_id);
}

Scheduler::Task Scheduler::get_next_task() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (ready_queue_.empty()) {
        return Task{};
    }

    Task task;

    switch (strategy_) {
        case Strategy::ROUND_ROBIN: {
            // Simple round-robin - take from front
            task = ready_queue_.front();
            ready_queue_.erase(ready_queue_.begin());
            break;
        }

        case Strategy::PRIORITY: {
            // Priority-based - highest priority first
            std::pop_heap(ready_queue_.begin(), ready_queue_.end());
            task = ready_queue_.back();
            ready_queue_.pop_back();
            break;
        }

        case Strategy::DATAFLOW: {
            // Dataflow - take first available block that is ready
            for (auto it = ready_queue_.begin(); it != ready_queue_.end(); ++it) {
                if (it->block && it->block->is_active() && it->block->is_ready()) {
                    task = *it;
                    ready_queue_.erase(it);
                    break;
                }
            }
            if (!task.block && !ready_queue_.empty()) {
                // If no block is ready, but queue is not empty, wait a bit or take first
                // For now, take first to avoid starvation if is_ready() is not perfectly implemented
                task = ready_queue_.front();
                ready_queue_.erase(ready_queue_.begin());
            }
            break;
        }

        case Strategy::REAL_TIME: {
            // Real-time - earliest deadline first
            if (!ready_queue_.empty()) {
                auto it = std::min_element(ready_queue_.begin(), ready_queue_.end(),
                                           [](const Task& a, const Task& b) {
                                               return a.deadline < b.deadline;
                                           });
                task = *it;
                ready_queue_.erase(it);
            }
            break;
        }

        default: {
            // Fallback to round-robin
            if (!ready_queue_.empty()) {
                task = ready_queue_.front();
                ready_queue_.erase(ready_queue_.begin());
            }
            break;
        }
    }

    return task;
}

void Scheduler::schedule_tasks() {
    worker_cv_.notify_all();
}