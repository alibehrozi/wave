package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that performs element-wise subtraction between two input streams.
 * <p>
 * Output: {@code out = in0 - in1}
 */
public class Subtract extends Block {

    /**
     * Create a Subtract block with custom name.
     * @param type Data type to process
     * @param name Block name
     */
    public Subtract(@NonNull DataType type, @NonNull String name) {
        super(name, nativeCreateSubtract(type.ordinal(), name));
    }

    /**
     * Create a Subtract block with default name.
     * @param type Data type to process
     */
    public Subtract(@NonNull DataType type) {
        this(type, "subtract");
    }

    private static native long nativeCreateSubtract(int type, String name);
}
