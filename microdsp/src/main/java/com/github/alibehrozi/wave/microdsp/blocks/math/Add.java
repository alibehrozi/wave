package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that performs element-wise addition of multiple input streams.
 * <p>
 * Output: {@code out = in0 + in1 + ... + in{N-1}}
 */
public class Add extends Block {

    /**
     * Create an Add block with custom number of inputs and name.
     * @param type      Data type to process
     * @param numInputs Number of input streams to sum
     * @param name      Block name
     */
    public Add(@NonNull DataType type, int numInputs, @NonNull String name) {
        super(name, nativeCreateAdd(type.ordinal(), numInputs, name));
    }

    /**
     * Create an Add block with custom number of inputs and default name.
     * @param type      Data type to process
     * @param numInputs Number of input streams to sum
     */
    public Add(@NonNull DataType type, int numInputs) {
        this(type, numInputs, "add");
    }

    /**
     * Create an Add block with 2 inputs and custom name.
     * @param type Data type to process
     * @param name Block name
     */
    public Add(@NonNull DataType type, @NonNull String name) {
        this(type, 2, name);
    }

    /**
     * Create an Add block with 2 inputs and default name.
     * @param type Data type to process
     */
    public Add(@NonNull DataType type) {
        this(type, 2, "add");
    }

    private static native long nativeCreateAdd(int type, int numInputs, String name);
}
