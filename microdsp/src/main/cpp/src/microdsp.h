#ifndef MICRODSP_H
#define MICRODSP_H

#include <jni.h>
#include <memory>
#include <unordered_map>
#include <atomic>
#include <mutex>
#include <type_traits>
#include <cstdint>
#include <vector>

class Block;
class Flowgraph;
class Port;

/**
 * @class MicroDSP
 * @brief Singleton management class for the DSP engine.
 *
 * MicroDSP handles the registration and lifecycle of DSP objects (Blocks, Flowgraphs, Ports)
 * and provides a global registry to access them via handles (int64_t).
 * It also manages the interaction between C++ and the Java Virtual Machine.
 */
class MicroDSP {
public:
    /**
     * @brief Get the singleton instance of MicroDSP
     * @return Reference to the MicroDSP instance
     */
    static MicroDSP& get_instance();

    /**
     * @brief Register a block in the global registry
     * @param block Shared pointer to the block
     * @return Unique handle for the block
     */
    int64_t register_block(std::shared_ptr<Block> block);

    /**
     * @brief Register a flowgraph in the global registry
     * @param flowgraph Shared pointer to the flowgraph
     * @return Unique handle for the flowgraph
     */
    int64_t register_flowgraph(std::shared_ptr<Flowgraph> flowgraph);

    /**
     * @brief Register a port in the global registry
     * @param port Pointer to the port
     * @return Unique handle for the port
     */
    int64_t register_port(Port* port);

    /**
     * @brief Get a block from the registry by its handle
     * @param handle Unique handle
     * @return Shared pointer to the block, or nullptr if not found
     */
    std::shared_ptr<Block> get_block(int64_t handle);

    /**
     * @brief Get a flowgraph from the registry by its handle
     * @param handle Unique handle
     * @return Shared pointer to the flowgraph, or nullptr if not found
     */
    std::shared_ptr<Flowgraph> get_flowgraph(int64_t handle);

    /**
     * @brief Get a port from the registry by its handle
     * @param handle Unique handle
     * @return Pointer to the port, or nullptr if not found
     */
    Port* get_port(int64_t handle);

    /**
     * @brief Get a block of a specific type from the registry
     * @tparam T The block type (must derive from Block)
     * @param handle Unique handle
     * @return Shared pointer to the typed block, or nullptr if not found or type mismatch
     */
    template<typename T>
    std::shared_ptr<T> get_block(int64_t handle) {
        static_assert(std::is_base_of<Block, T>::value, "T must derive from Block");

        std::lock_guard<std::mutex> lock(registry_mutex_);
        auto it = block_registry_.find(handle);
        if (it != block_registry_.end()) {
            return std::dynamic_pointer_cast<T>(it->second);
        }
        return nullptr;
    }

    /**
     * @brief Get a flowgraph of a specific type from the registry
     * @tparam T The flowgraph type (must derive from Flowgraph)
     * @param handle Unique handle
     * @return Shared pointer to the typed flowgraph, or nullptr if not found or type mismatch
     */
    template<typename T>
    std::shared_ptr<T> get_flowgraph(int64_t handle) {
        static_assert(std::is_base_of<Flowgraph, T>::value, "T must derive from Flowgraph");

        std::lock_guard<std::mutex> lock(registry_mutex_);
        auto it = flowgraph_registry_.find(handle);
        if (it != flowgraph_registry_.end()) {
            return std::dynamic_pointer_cast<T>(it->second);
        }
        return nullptr;
    }

    /**
     * @brief Unregister an object from the global registry
     * @param handle The handle of the object to unregister
     */
    void unregister_object(int64_t handle);

    /**
     * @brief Store the Java VM pointer for JNI interaction
     * @param jvm Pointer to JavaVM
     */
    void set_java_vm(JavaVM* jvm);

    /**
     * @brief Get the stored Java VM pointer
     * @return Pointer to JavaVM
     */
    JavaVM* get_java_vm();

    /**
     * @brief Get the JNI environment for the current thread
     * @return Pointer to JNIEnv, or nullptr if not attached
     */
    JNIEnv* get_jni_env();

    /**
     * @brief Attach the current thread to the Java VM
     */
    void attach_current_thread();

    /**
     * @brief Detach the current thread from the Java VM
     */
    void detach_current_thread();

private:
    MicroDSP() = default;
    ~MicroDSP() = default;

    JavaVM* java_vm_ = nullptr;
    std::atomic<int64_t> next_handle_{1};

    std::unordered_map<int64_t, std::shared_ptr<Block>> block_registry_;
    std::unordered_map<int64_t, std::shared_ptr<Flowgraph>> flowgraph_registry_;
    std::unordered_map<int64_t, Port*> port_registry_;

    std::mutex registry_mutex_;
    std::mutex jvm_mutex_;
};

#endif