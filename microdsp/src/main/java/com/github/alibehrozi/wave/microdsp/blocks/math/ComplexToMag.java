package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that computes the magnitude (absolute value) of a complex IQ stream.
 * <p>
 * Input: {@link DataType#COMPLEX_FLOAT} or {@link DataType#COMPLEX_DOUBLE}
 * Output: {@link DataType#FLOAT} or {@link DataType#DOUBLE}
 * <p>
 * Output: {@code out = sqrt(I^2 + Q^2)}
 */
public class ComplexToMag extends Block {

    /**
     * Create a ComplexToMag block.
     * @param type Input data type (must be complex)
     * @param name Block name
     */
    public ComplexToMag(@NonNull DataType type, @NonNull String name) {
        super(name, nativeCreateComplexToMag(type.ordinal(), name));
    }

    /**
     * Create a ComplexToMag block with default name.
     * @param type Input data type
     */
    public ComplexToMag(@NonNull DataType type) {
        this(type, "complex_to_mag");
    }

    /**
     * Create a ComplexToMag block with default parameters (COMPLEX_FLOAT).
     */
    public ComplexToMag() {
        this(DataType.COMPLEX_FLOAT, "complex_to_mag");
    }

    private static native long nativeCreateComplexToMag(int type, String name);
}
