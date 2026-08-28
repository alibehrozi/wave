package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that combines separate Real (I) and Imaginary (Q) float streams into a complex IQ stream.
 * <p>
 * Input 0 ("real"): {@link DataType#FLOAT} or {@link DataType#DOUBLE}
 * Input 1 ("imag"): {@link DataType#FLOAT} or {@link DataType#DOUBLE}
 * Output ("out"): {@link DataType#COMPLEX_FLOAT} or {@link DataType#COMPLEX_DOUBLE}
 */
public class RealImagToComplex extends Block {

    /**
     * Create a RealImagToComplex block with custom name.
     * @param type Output data type (must be complex)
     * @param name Block name
     */
    public RealImagToComplex(@NonNull DataType type, @NonNull String name) {
        super(name, nativeCreateRealImagToComplex(type.ordinal(), name));
    }

    /**
     * Create a RealImagToComplex block with default name.
     * @param type Output data type (must be complex)
     */
    public RealImagToComplex(@NonNull DataType type) {
        this(type, "real_imag_to_complex");
    }

    /**
     * Create a RealImagToComplex block with default parameters (COMPLEX_FLOAT).
     */
    public RealImagToComplex() {
        this(DataType.COMPLEX_FLOAT, "real_imag_to_complex");
    }

    private static native long nativeCreateRealImagToComplex(int type, String name);
}
