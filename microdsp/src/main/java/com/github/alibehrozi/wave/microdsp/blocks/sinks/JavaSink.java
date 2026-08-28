package com.github.alibehrozi.wave.microdsp.blocks.sinks;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

import java.nio.ByteBuffer;

/**
 * High-performance sink block that pulls data from the DSP pipeline to Java.
 * Use a direct ByteBuffer for zero-copy native access.
 */
public class JavaSink extends Block {

    /**
     * Create a JavaSink
     * @param type     Data type to receive
     * @param capacity Internal buffer capacity in items
     * @param name     Block name
     */
    public JavaSink(@NonNull DataType type, int capacity, String name) {
        super(name, nativeCreateJavaSink(type.ordinal(), capacity, name));
    }

    public JavaSink(@NonNull DataType type, int capacity) {
        this(type, capacity, "java_sink");
    }

    /**
     * Pull data from the DSP pipeline.
     * @param buffer   A direct ByteBuffer to receive the data.
     * @param maxCount Maximum number of items (not bytes) to pull.
     * @return Number of items actually pulled.
     */
    public int pull(@NonNull ByteBuffer buffer, int maxCount) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer must be direct for high-performance interop");
        }
        return nativePull(nativeHandle, buffer, maxCount);
    }

    /**
     * Get number of items available to be pulled.
     */
    public int readAvailable() {
        return nativeReadAvailable(nativeHandle);
    }

    private static native long nativeCreateJavaSink(int type, int capacity, String name);
    private native int nativePull(long handle, ByteBuffer buffer, int maxCount);
    private native int nativeReadAvailable(long handle);
}
