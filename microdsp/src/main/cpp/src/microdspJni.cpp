#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "microdsp.h"
#include "core/Block.h"
#include "core/Flowgraph.h"
#include "core/Port.h"

#define LOG_TAG "MicroDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// JNI Initialization
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    MicroDSP::get_instance().set_java_vm(vm);
    LOGI("MicroDSP JNI wrapper loaded successfully");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("MicroDSP JNI wrapper unloaded");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeCreateFlowgraph(JNIEnv* env, jobject thiz, jstring name) {
    if (name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Flowgraph name cannot be null");
        return 0;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    if (c_name == nullptr || strlen(c_name) == 0) {
        env->ReleaseStringUTFChars(name, c_name);
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Flowgraph name cannot be empty");
        return 0;
    }

    try {
        auto flowgraph = std::make_shared<Flowgraph>(c_name);
        long handle = MicroDSP::get_instance().register_flowgraph(flowgraph);
        env->ReleaseStringUTFChars(name, c_name);
        LOGI("Created Flowgraph: %s with handle: %lld", c_name, handle);
        return handle;
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(name, c_name);
        LOGE("Failed to create flowgraph: %s", e.what());
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeDestroyFlowgraph(JNIEnv* env, jobject thiz, jlong handle) {
    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    if (flowgraph) {
        MicroDSP::get_instance().unregister_object(handle);
        LOGI("Destroyed Flowgraph with handle: %lld", handle);
    } else {
        LOGE("Failed to destroy Flowgraph - invalid handle: %lld", handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeStart(JNIEnv* env, jobject thiz, jlong handle) {
    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    if (!flowgraph) {
        LOGE("Flowgraph start failed - invalid handle: %lld", handle);
        return JNI_FALSE;
    }

    try {
        flowgraph->start();
        LOGI("Flowgraph started for handle: %lld", handle);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Flowgraph start failed: %s", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeStop(JNIEnv* env, jobject thiz, jlong handle) {
    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    if (flowgraph) {
        flowgraph->stop();
        LOGI("Flowgraph stopped for handle: %lld", handle);
    } else {
        LOGE("Flowgraph stop failed - invalid handle: %lld", handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeWaitForCompletion(JNIEnv* env, jobject thiz, jlong handle) {
    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    if (flowgraph) {
        flowgraph->wait();
        LOGI("Flowgraph completed for handle: %lld", handle);
    } else {
        LOGE("Flowgraph wait failed - invalid handle: %lld", handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeRun(JNIEnv* env, jobject thiz, jlong handle) {
    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    if (flowgraph) {
        flowgraph->run();
        LOGI("Flowgraph run completed for handle: %lld", handle);
    } else {
        LOGE("Flowgraph run failed - invalid handle: %lld", handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeIsRunning(JNIEnv* env, jobject thiz, jlong handle) {
    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    if (flowgraph) {
        bool is_running = flowgraph->is_running();
        LOGI("Flowgraph is_running: %s for handle: %lld", is_running ? "true" : "false", handle);
        return is_running ? JNI_TRUE : JNI_FALSE;
    } else {
        LOGE("Flowgraph is_running check failed - invalid handle: %lld", handle);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeConnect(JNIEnv* env, jobject thiz, jlong handle,
                                                                     jlong src_block, jstring src_port,
                                                                     jlong dst_block, jstring dst_port) {
    if (src_port == nullptr || dst_port == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Port names cannot be null");
        return JNI_FALSE;
    }

    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    auto src = MicroDSP::get_instance().get_block(src_block);
    auto dst = MicroDSP::get_instance().get_block(dst_block);

    if (!flowgraph) {
        LOGE("Connection failed - invalid flowgraph handle: %lld", handle);
        return JNI_FALSE;
    }

    if (!src) {
        LOGE("Connection failed - invalid source block handle: %lld", src_block);
        return JNI_FALSE;
    }

    if (!dst) {
        LOGE("Connection failed - invalid destination block handle: %lld", dst_block);
        return JNI_FALSE;
    }

    const char* c_src_port = env->GetStringUTFChars(src_port, nullptr);
    const char* c_dst_port = env->GetStringUTFChars(dst_port, nullptr);

    bool result = flowgraph->connect(src, c_src_port, dst, c_dst_port);

    env->ReleaseStringUTFChars(src_port, c_src_port);
    env->ReleaseStringUTFChars(dst_port, c_dst_port);

    if (result) {
        LOGI("Connection successful: %s:%s -> %s:%s",
             src->get_name().c_str(), c_src_port,
             dst->get_name().c_str(), c_dst_port);
    } else {
        LOGE("Connection failed: %s:%s -> %s:%s",
             src->get_name().c_str(), c_src_port,
             dst->get_name().c_str(), c_dst_port);
    }

    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeDisconnect(JNIEnv* env, jobject thiz, jlong handle,
                                                                        jlong src_block, jstring src_port,
                                                                        jlong dst_block, jstring dst_port) {
    if (src_port == nullptr || dst_port == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Port names cannot be null");
        return;
    }

    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    auto src = MicroDSP::get_instance().get_block(src_block);
    auto dst = MicroDSP::get_instance().get_block(dst_block);

    if (!flowgraph || !src || !dst) {
        LOGE("Disconnect failed - invalid handles: flowgraph=%lld, src=%lld, dst=%lld",
             handle, src_block, dst_block);
        return;
    }

    const char* c_src_port = env->GetStringUTFChars(src_port, nullptr);
    const char* c_dst_port = env->GetStringUTFChars(dst_port, nullptr);

    flowgraph->disconnect(src, c_src_port, dst, c_dst_port);

    env->ReleaseStringUTFChars(src_port, c_src_port);
    env->ReleaseStringUTFChars(dst_port, c_dst_port);

    LOGI("Disconnected: %s:%s -> %s:%s",
         src->get_name().c_str(), c_src_port,
         dst->get_name().c_str(), c_dst_port);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeAreConnected(JNIEnv* env, jobject thiz, jlong handle,
                                                                          jlong src_block, jstring src_port,
                                                                          jlong dst_block, jstring dst_port) {
    if (src_port == nullptr || dst_port == nullptr) {
        return JNI_FALSE;
    }

    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    auto src = MicroDSP::get_instance().get_block(src_block);
    auto dst = MicroDSP::get_instance().get_block(dst_block);

    if (!flowgraph || !src || !dst) {
        return JNI_FALSE;
    }

    const char* c_src_port = env->GetStringUTFChars(src_port, nullptr);
    const char* c_dst_port = env->GetStringUTFChars(dst_port, nullptr);

    bool result = flowgraph->are_connected(src, c_src_port, dst, c_dst_port);

    env->ReleaseStringUTFChars(src_port, c_src_port);
    env->ReleaseStringUTFChars(dst_port, c_dst_port);

    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeAddBlock(JNIEnv* env, jobject thiz, jlong handle, jlong block_handle) {
    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    auto block = MicroDSP::get_instance().get_block(block_handle);

    if (flowgraph && block) {
        flowgraph->add_block(block);
        LOGI("Added block '%s' to Flowgraph", block->get_name().c_str());
    } else {
        LOGE("Add block failed - invalid handles: flowgraph=%lld, block=%lld", handle, block_handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Flowgraph_nativeRemoveBlock(JNIEnv* env, jobject thiz, jlong handle, jlong block_handle) {
    auto flowgraph = MicroDSP::get_instance().get_flowgraph(handle);
    auto block = MicroDSP::get_instance().get_block(block_handle);

    if (flowgraph && block) {
        flowgraph->remove_block(block);
        LOGI("Removed block '%s' from Flowgraph", block->get_name().c_str());
    } else {
        LOGE("Remove block failed - invalid handles: flowgraph=%lld, block=%lld", handle, block_handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeConnect(JNIEnv* env, jobject thiz, jlong handle, jlong other_handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    auto other = MicroDSP::get_instance().get_port(other_handle);

    if (!port || !other) {
        LOGE("Port connect failed - invalid handles: %lld, %lld", handle, other_handle);
        return JNI_FALSE;
    }

    try {
        bool result = port->connect(other);
        LOGI("Port connect: %s -> %s %s",
             port->get_name().c_str(), other->get_name().c_str(),
             result ? "success" : "failed");
        return result ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("Port connect error: %s", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeDisconnect(JNIEnv* env, jobject thiz, jlong handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    if (port) {
        port->disconnect();
        LOGI("Port disconnected: %s (handle: %lld)", port->get_name().c_str(), handle);
    } else {
        LOGE("Port disconnect failed - invalid handle: %lld", handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeDisconnectFrom(JNIEnv* env, jobject thiz, jlong handle, jlong other_handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    auto other = MicroDSP::get_instance().get_port(other_handle);

    if (port && other) {
        port->disconnect(other);
        LOGI("Port disconnected: %s -> %s", port->get_name().c_str(), other->get_name().c_str());
    } else {
        LOGE("Port disconnect_from failed - invalid handles: %lld, %lld", handle, other_handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeIsConnected(JNIEnv* env, jobject thiz, jlong handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    if (port) {
        bool is_connected = port->is_connected();
        LOGI("Port is_connected: %s for handle: %lld", is_connected ? "true" : "false", handle);
        return is_connected ? JNI_TRUE : JNI_FALSE;
    } else {
        LOGE("Port is_connected check failed - invalid handle: %lld", handle);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeIsConnectedTo(JNIEnv* env, jobject thiz, jlong handle, jlong other_handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    auto other = MicroDSP::get_instance().get_port(other_handle);

    if (port && other) {
        return port->is_connected_to(other) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeGetType(JNIEnv* env, jobject thiz, jlong handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    if (port) {
        return static_cast<jint>(port->get_type());
    } else {
        LOGE("Port get_type failed - invalid handle: %lld", handle);
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeGetDirection(JNIEnv* env, jobject thiz, jlong handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    if (port) {
        return static_cast<jint>(port->get_direction());
    } else {
        LOGE("Port get_direction failed - invalid handle: %lld", handle);
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeGetName(JNIEnv* env, jobject thiz, jlong handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    if (port) {
        return env->NewStringUTF(port->get_name().c_str());
    } else {
        LOGE("Port get_name failed - invalid handle: %lld", handle);
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Port_nativeDestroyPort(JNIEnv* env, jobject thiz, jlong handle) {
    auto port = MicroDSP::get_instance().get_port(handle);
    if (port) {
        // Port is non-owning, just unregister
        MicroDSP::get_instance().unregister_object(handle);
        LOGI("Destroyed Port with handle: %lld", handle);
    } else {
        LOGE("Failed to destroy Port - invalid handle: %lld", handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeStart(JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        bool result = block->start();
        LOGI("Block start %s for handle: %lld", result ? "succeeded" : "failed", handle);
        return result ? JNI_TRUE : JNI_FALSE;
    } else {
        LOGE("Block start failed - invalid handle: %lld", handle);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeStop(JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        block->stop();
        LOGI("Block stopped for handle: %lld", handle);
    } else {
        LOGE("Block stop failed - invalid handle: %lld", handle);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeIsActive(JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        bool is_active = block->is_active();
        LOGI("Block is_active: %s for handle: %lld", is_active ? "true" : "false", handle);
        return is_active ? JNI_TRUE : JNI_FALSE;
    } else {
        LOGE("Block is_active check failed - invalid handle: %lld", handle);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetInputPortCount(JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        int count = block->get_input_port_count();
        LOGI("Block input port count: %d for handle: %lld", count, handle);
        return count;
    } else {
        LOGE("Block get_input_port_count failed - invalid handle: %lld", handle);
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetOutputPortCount(JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        int count = block->get_output_port_count();
        LOGI("Block output port count: %d for handle: %lld", count, handle);
        return count;
    } else {
        LOGE("Block get_output_port_count failed - invalid handle: %lld", handle);
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetInputPortName(JNIEnv* env, jobject thiz, jlong handle, jint index) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        std::string name = block->get_input_port_name(index);
        return env->NewStringUTF(name.c_str());
    } else {
        LOGE("Block get_input_port_name failed - invalid handle: %lld", handle);
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetOutputPortName(JNIEnv* env, jobject thiz, jlong handle, jint index) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        std::string name = block->get_output_port_name(index);
        return env->NewStringUTF(name.c_str());
    } else {
        LOGE("Block get_output_port_name failed - invalid handle: %lld", handle);
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetInputPort(JNIEnv* env, jobject thiz, jlong handle, jstring port_name) {
    if (port_name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Port name cannot be null");
        return 0;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block get_input_port failed - invalid handle: %lld", handle);
        return 0;
    }

    const char* c_port_name = env->GetStringUTFChars(port_name, nullptr);
    auto port = block->get_input_port(c_port_name);
    env->ReleaseStringUTFChars(port_name, c_port_name);

    if (port) {
        long port_handle = MicroDSP::get_instance().register_port(port);
        LOGI("Got input port %s with handle: %lld for block: %lld", c_port_name, port_handle, handle);
        return port_handle;
    } else {
        LOGE("Block get_input_port failed - port not found: %s for block: %lld", c_port_name, handle);
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetOutputPort(JNIEnv* env, jobject thiz, jlong handle, jstring port_name) {
    if (port_name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Port name cannot be null");
        return 0;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block get_output_port failed - invalid handle: %lld", handle);
        return 0;
    }

    const char* c_port_name = env->GetStringUTFChars(port_name, nullptr);
    auto port = block->get_output_port(c_port_name);
    env->ReleaseStringUTFChars(port_name, c_port_name);

    if (port) {
        long port_handle = MicroDSP::get_instance().register_port(port);
        LOGI("Got output port %s with handle: %lld for block: %lld", c_port_name, port_handle, handle);
        return port_handle;
    } else {
        LOGE("Block get_output_port failed - port not found: %s for block: %lld", c_port_name, handle);
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeSetIntParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name, jint value) {
    if (name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Parameter name cannot be null");
        return;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block set_int_parameter failed - invalid handle: %lld", handle);
        return;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    block->set_parameter(c_name, static_cast<int>(value));
    env->ReleaseStringUTFChars(name, c_name);

    LOGI("Block set_int_parameter: %s = %d for handle: %lld", c_name, value, handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeSetDoubleParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name, jdouble value) {
    if (name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Parameter name cannot be null");
        return;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block set_double_parameter failed - invalid handle: %lld", handle);
        return;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    block->set_parameter(c_name, static_cast<double>(value));
    env->ReleaseStringUTFChars(name, c_name);

    LOGI("Block set_double_parameter: %s = %f for handle: %lld", c_name, value, handle);
}


extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeSetStringParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name, jstring value) {
    if (name == nullptr || value == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/NullPointerException"),
                      "Parameter name or value is null");
        return;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block set_string_parameter failed - invalid handle: %lld", handle);
        return;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    const char* c_value = env->GetStringUTFChars(value, nullptr);
    block->set_parameter(c_name, std::string(c_value));
    env->ReleaseStringUTFChars(name, c_name);
    env->ReleaseStringUTFChars(value, c_value);

    LOGI("Block set_string_parameter: %s = %s for handle: %lld", c_name, c_value, handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeSetBooleanParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name, jboolean value) {
    if (name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Parameter name cannot be null");
        return;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block set_boolean_parameter failed - invalid handle: %lld", handle);
        return;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    block->set_parameter(c_name, value == JNI_TRUE);
    env->ReleaseStringUTFChars(name, c_name);

    LOGI("Block set_boolean_parameter: %s = %s for handle: %lld",
         c_name, (value == JNI_TRUE) ? "true" : "false", handle);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetIntParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name, jint defaultValue) {
    if (name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Parameter name cannot be null");
        return defaultValue;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block get_int_parameter failed - invalid handle: %lld", handle);
        return defaultValue;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    int value = block->get_int_parameter(c_name, static_cast<int>(defaultValue));
    env->ReleaseStringUTFChars(name, c_name);

    LOGI("Block get_int_parameter: %s = %d for handle: %lld", c_name, value, handle);
    return static_cast<jint>(value);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetDoubleParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name, jdouble defaultValue) {
    if (name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Parameter name cannot be null");
        return defaultValue;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block get_double_parameter failed - invalid handle: %lld", handle);
        return defaultValue;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    double value = block->get_double_parameter(c_name, static_cast<double>(defaultValue));
    env->ReleaseStringUTFChars(name, c_name);

    LOGI("Block get_double_parameter: %s = %f for handle: %lld", c_name, value, handle);
    return static_cast<jdouble>(value);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetStringParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name, jstring defaultValue) {
    if (name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Parameter name cannot be null");
        return defaultValue;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block get_string_parameter failed - invalid handle: %lld", handle);
        return defaultValue;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    const char* c_default = defaultValue ? env->GetStringUTFChars(defaultValue, nullptr) : "";
    std::string value = block->get_string_parameter(c_name, std::string(c_default));
    env->ReleaseStringUTFChars(name, c_name);
    if (defaultValue) {
        env->ReleaseStringUTFChars(defaultValue, c_default);
    }

    LOGI("Block get_string_parameter: %s = %s for handle: %lld", c_name, value.c_str(), handle);
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetBooleanParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name, jboolean defaultValue) {
    if (name == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Parameter name cannot be null");
        return defaultValue;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block get_boolean_parameter failed - invalid handle: %lld", handle);
        return defaultValue;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    bool value = block->get_bool_parameter(c_name, defaultValue == JNI_TRUE);
    env->ReleaseStringUTFChars(name, c_name);

    LOGI("Block get_boolean_parameter: %s = %s for handle: %lld",
         c_name, value ? "true" : "false", handle);
    return value ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeHasParameter(JNIEnv* env, jobject thiz, jlong handle, jstring name) {
    if (name == nullptr) {
        return JNI_FALSE;
    }

    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block has_parameter failed - invalid handle: %lld", handle);
        return JNI_FALSE;
    }

    const char* c_name = env->GetStringUTFChars(name, nullptr);
    bool result = block->has_parameter(c_name);
    env->ReleaseStringUTFChars(name, c_name);

    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeGetParameterNames(JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (!block) {
        LOGE("Block get_parameter_names failed - invalid handle: %lld", handle);
        return nullptr;
    }

    std::vector<std::string> names = block->get_parameter_names();

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(names.size(), stringClass, nullptr);

    for (size_t i = 0; i < names.size(); i++) {
        jstring str = env->NewStringUTF(names[i].c_str());
        env->SetObjectArrayElement(array, i, str);
        env->DeleteLocalRef(str);
    }

    LOGI("Block get_parameter_names: %zu parameters for handle: %lld", names.size(), handle);
    return array;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeResetParameters(JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        block->reset_parameters();
        LOGI("Block reset_parameters for handle: %lld", handle);
    } else {
        LOGE("Block reset_parameters failed - invalid handle: %lld", handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_alibehrozi_wave_microdsp_core_Block_nativeDestroyBlock(JNIEnv* env, jobject thiz, jlong handle) {
    auto block = MicroDSP::get_instance().get_block(handle);
    if (block) {
        MicroDSP::get_instance().unregister_object(handle);
        LOGI("Destroyed Block with handle: %lld", handle);
    } else {
        LOGE("Failed to destroy Block - invalid handle: %lld", handle);
    }
}
