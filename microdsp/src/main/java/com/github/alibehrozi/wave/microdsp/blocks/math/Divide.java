package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that performs element-wise division between two input streams.
 * <p>
 * Output: {@code out = in0 / (in1 + eps)}
 */
public class Divide extends Block {

    /**
     * Create a Divide block with custom epsilon and name.
     * @param type Data type to process
     * @param eps  Small value added to denominator to avoid division by zero
     * @param name Block name
     */
    public Divide(@NonNull DataType type, float eps, @NonNull String name) {
        super(name, nativeCreateDivide(type.ordinal(), eps, name));
    }

    /**
     * Create a Divide block with custom epsilon and default name.
     * @param type Data type to process
     * @param eps  Small value added to denominator
     */
    public Divide(@NonNull DataType type, float eps) {
        this(type, eps, "divide");
    }

    /**
     * Create a Divide block with default epsilon and custom name.
     * @param type Data type to process
     * @param name Block name
     */
    public Divide(@NonNull DataType type, @NonNull String name) {
        this(type, 1e-12f, name);
    }

    /**
     * Create a Divide block with default epsilon and default name.
     * @param type Data type to process
     */
    public Divide(@NonNull DataType type) {
        this(type, 1e-12f, "divide");
    }

    /**
     * Set a new epsilon value.
     * @param eps New epsilon value
     */
    public void setEps(float eps) {
        nativeSetEps(nativeHandle, eps);
    }

    private static native long nativeCreateDivide(int type, float eps, String name);
    private native void nativeSetEps(long handle, float eps);
}
