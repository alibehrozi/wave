#include "microdsp.h"
#include "core/Block.h"
#include "core/Flowgraph.h"
#include "core/Port.h"
#include <mutex>
#include <android/log.h>

#define LOG_TAG "MicroDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

MicroDSP &MicroDSP::get_instance() {
    static MicroDSP instance;
    return instance;
}

int64_t MicroDSP::register_block(std::shared_ptr<Block> block) {
    std::lock_guard<std::mutex> lock(registry_mutex_);
    int64_t handle = next_handle_++;
    block_registry_[handle] = block;
    LOGI("Registered block '%s' with handle: %lld", block->get_name().c_str(), (long long)handle);
    return handle;
}

int64_t MicroDSP::register_flowgraph(std::shared_ptr<Flowgraph> flowgraph) {
    std::lock_guard<std::mutex> lock(registry_mutex_);
    int64_t handle = next_handle_++;
    flowgraph_registry_[handle] = flowgraph;
    LOGI("Registered flowgraph '%s' with handle: %lld", flowgraph->get_name().c_str(), (long long)handle);
    return handle;
}

int64_t MicroDSP::register_port(Port* port) {
    std::lock_guard<std::mutex> lock(registry_mutex_);
    int64_t handle = next_handle_++;
    port_registry_[handle] = port;
    LOGI("Registered port '%s' with handle: %lld", port->get_name().c_str(), (long long)handle);
    return handle;
}

std::shared_ptr<Block> MicroDSP::get_block(int64_t handle) {
    std::lock_guard<std::mutex> lock(registry_mutex_);
    auto it = block_registry_.find(handle);
    return it != block_registry_.end() ? it->second : nullptr;
}

std::shared_ptr<Flowgraph> MicroDSP::get_flowgraph(int64_t handle) {
    std::lock_guard<std::mutex> lock(registry_mutex_);
    auto it = flowgraph_registry_.find(handle);
    return it != flowgraph_registry_.end() ? it->second : nullptr;
}

Port* MicroDSP::get_port(int64_t handle) {
    std::lock_guard<std::mutex> lock(registry_mutex_);
    auto it = port_registry_.find(handle);
    return it != port_registry_.end() ? it->second : nullptr;
}

void MicroDSP::unregister_object(int64_t handle) {
    std::lock_guard<std::mutex> lock(registry_mutex_);
    block_registry_.erase(handle);
    flowgraph_registry_.erase(handle);
    port_registry_.erase(handle);
    LOGI("Unregistered object with handle: %lld", (long long)handle);
}

void MicroDSP::set_java_vm(JavaVM *jvm) {
    std::lock_guard<std::mutex> lock(jvm_mutex_);
    java_vm_ = jvm;
}

JavaVM *MicroDSP::get_java_vm() {
    std::lock_guard<std::mutex> lock(jvm_mutex_);
    return java_vm_;
}

JNIEnv *MicroDSP::get_jni_env() {
    std::lock_guard<std::mutex> lock(jvm_mutex_);
    if (!java_vm_) return nullptr;

    JNIEnv *env = nullptr;
    jint result = java_vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);

    if (result == JNI_EDETACHED) {
        // Attach current thread to JVM
        java_vm_->AttachCurrentThread(&env, nullptr);
    }

    return env;
}

void MicroDSP::attach_current_thread() {
    std::lock_guard<std::mutex> lock(jvm_mutex_);
    if (java_vm_) {
        JNIEnv* env = nullptr;
        java_vm_->AttachCurrentThread(&env, nullptr);
    }
}

void MicroDSP::detach_current_thread() {
    std::lock_guard<std::mutex> lock(jvm_mutex_);
    if (java_vm_) {
        java_vm_->DetachCurrentThread();
    }
}