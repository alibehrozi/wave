package com.github.alibehrozi.wave.microdsp.blocks.sources;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

import java.nio.ByteBuffer;

/**
 * High-performance source block that pushes data from Java to the DSP pipeline.
 * Use a direct ByteBuffer for zero-copy native access.
 */
public class JavaSource extends Block {

    /**
     * Create a JavaSource
     * @param type     Data type to output
     * @param capacity Internal buffer capacity in items
     * @param name     Block name
     */
    public JavaSource(@NonNull DataType type, int capacity, String name) {
        super(name, nativeCreateJavaSource(type.ordinal(), capacity, name));
    }

    public JavaSource(@NonNull DataType type, int capacity) {
        this(type, capacity, "java_source");
    }

    /**
     * Push data into the DSP pipeline.
     * @param buffer A direct ByteBuffer containing the data.
     * @param count  Number of items (not bytes) to push.
     * @return Number of items actually pushed.
     */
    public int push(@NonNull ByteBuffer buffer, int count) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer must be direct for high-performance interop");
        }
        return nativePush(nativeHandle, buffer, count);
    }

    /**
     * Get number of items that can be pushed without blocking.
     */
    public int writeAvailable() {
        return nativeWriteAvailable(nativeHandle);
    }

    private static native long nativeCreateJavaSource(int type, int capacity, String name);
    private native int nativePush(long handle, ByteBuffer buffer, int count);
    private native int nativeWriteAvailable(long handle);
}
