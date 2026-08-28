package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that performs element-wise multiplication of multiple input streams.
 * <p>
 * Output: {@code out = in0 * in1 * ... * in{N-1}}
 */
public class Multiply extends Block {

    /**
     * Create a Multiply block with custom number of inputs and name.
     * @param type      Data type to process
     * @param numInputs Number of input streams to multiply
     * @param name      Block name
     */
    public Multiply(@NonNull DataType type, int numInputs, @NonNull String name) {
        super(name, nativeCreateMultiply(type.ordinal(), numInputs, name));
    }

    /**
     * Create a Multiply block with custom number of inputs and default name.
     * @param type      Data type to process
     * @param numInputs Number of input streams to multiply
     */
    public Multiply(@NonNull DataType type, int numInputs) {
        this(type, numInputs, "multiply");
    }

    /**
     * Create a Multiply block with 2 inputs and custom name.
     * @param type Data type to process
     * @param name Block name
     */
    public Multiply(@NonNull DataType type, @NonNull String name) {
        this(type, 2, name);
    }

    /**
     * Create a Multiply block with 2 inputs and default name.
     * @param type Data type to process
     */
    public Multiply(@NonNull DataType type) {
        this(type, 2, "multiply");
    }

    private static native long nativeCreateMultiply(int type, int numInputs, String name);
}
