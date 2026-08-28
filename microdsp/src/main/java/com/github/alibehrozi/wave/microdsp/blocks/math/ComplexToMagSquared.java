package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that computes the magnitude squared (power) of a complex IQ stream.
 * <p>
 * Input: {@link DataType#COMPLEX_FLOAT} or {@link DataType#COMPLEX_DOUBLE}
 * Output: {@link DataType#FLOAT} or {@link DataType#DOUBLE}
 * <p>
 * Output: {@code out = I^2 + Q^2}
 */
public class ComplexToMagSquared extends Block {

    /**
     * Create a ComplexToMagSquared block.
     * @param type Input data type (must be complex)
     * @param name Block name
     */
    public ComplexToMagSquared(@NonNull DataType type, @NonNull String name) {
        super(name, nativeCreateComplexToMagSquared(type.ordinal(), name));
    }

    /**
     * Create a ComplexToMagSquared block with default name.
     * @param type Input data type
     */
    public ComplexToMagSquared(@NonNull DataType type) {
        this(type, "complex_to_mag_squared");
    }

    /**
     * Create a ComplexToMagSquared block with default parameters (COMPLEX_FLOAT).
     */
    public ComplexToMagSquared() {
        this(DataType.COMPLEX_FLOAT, "complex_to_mag_squared");
    }

    private static native long nativeCreateComplexToMagSquared(int type, String name);
}
