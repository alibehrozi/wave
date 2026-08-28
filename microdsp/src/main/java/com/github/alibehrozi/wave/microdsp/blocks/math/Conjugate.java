package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that computes the complex conjugate of an IQ stream.
 * <p>
 * Output: {@code out = I - j * Q}
 */
public class Conjugate extends Block {

    /**
     * Create a Conjugate block.
     * @param type Input data type (must be complex)
     * @param name Block name
     */
    public Conjugate(@NonNull DataType type, @NonNull String name) {
        super(name, nativeCreateConjugate(type.ordinal(), name));
    }

    /**
     * Create a Conjugate block with default name.
     * @param type Input data type
     */
    public Conjugate(@NonNull DataType type) {
        this(type, "conjugate");
    }

    /**
     * Create a Conjugate block with default parameters (COMPLEX_FLOAT).
     */
    public Conjugate() {
        this(DataType.COMPLEX_FLOAT, "conjugate");
    }

    private static native long nativeCreateConjugate(int type, String name);
}
