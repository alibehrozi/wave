package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that computes the absolute value of real signals.
 * <p>
 * Output: {@code out = |in|}
 */
public class Abs extends Block {

    /**
     * Create an Abs block.
     * @param type Data type to process
     * @param name Block name
     */
    public Abs(@NonNull DataType type, @NonNull String name) {
        super(name, nativeCreateAbs(type.ordinal(), name));
    }

    /**
     * Create an Abs block with default name.
     * @param type Data type to process
     */
    public Abs(@NonNull DataType type) {
        this(type, "abs");
    }

    private static native long nativeCreateAbs(int type, String name);
}
