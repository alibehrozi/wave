package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that splits a complex IQ stream into separate Real (I) and Imaginary (Q) float streams.
 * <p>
 * Input: {@link DataType#COMPLEX_FLOAT} or {@link DataType#COMPLEX_DOUBLE}
 * Output 0 ("real"): {@link DataType#FLOAT} or {@link DataType#DOUBLE}
 * Output 1 ("imag"): {@link DataType#FLOAT} or {@link DataType#DOUBLE}
 */
public class ComplexToRealImag extends Block {

    /**
     * Create a ComplexToRealImag block.
     * @param type Input data type (must be complex)
     * @param name Block name
     */
    public ComplexToRealImag(@NonNull DataType type, @NonNull String name) {
        super(name, nativeCreateComplexToRealImag(type.ordinal(), name));
    }

    /**
     * Create a ComplexToRealImag block with default name.
     * @param type Input data type
     */
    public ComplexToRealImag(@NonNull DataType type) {
        this(type, "complex_to_real_imag");
    }

    /**
     * Create a ComplexToRealImag block with default parameters (COMPLEX_FLOAT).
     */
    public ComplexToRealImag() {
        this(DataType.COMPLEX_FLOAT, "complex_to_real_imag");
    }

    private static native long nativeCreateComplexToRealImag(int type, String name);
}
