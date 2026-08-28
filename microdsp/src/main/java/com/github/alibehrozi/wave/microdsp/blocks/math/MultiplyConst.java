package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that multiplies the input stream by a scalar or complex constant factor (gain/attenuation).
 * <p>
 * Output: {@code out = in * constant}
 */
public class MultiplyConst extends Block {

    /**
     * Create a MultiplyConst block for real constant with custom name.
     * @param type     Data type to process
     * @param constant Real constant factor
     * @param name     Block name
     */
    public MultiplyConst(@NonNull DataType type, float constant, @NonNull String name) {
        this(type, constant, 0.0f, name);
    }

    /**
     * Create a MultiplyConst block for complex constant with custom name.
     * @param type      Data type to process
     * @param constReal Real part of the constant factor
     * @param constImag Imaginary part of the constant factor
     * @param name      Block name
     */
    public MultiplyConst(@NonNull DataType type, float constReal, float constImag, @NonNull String name) {
        super(name, nativeCreateMultiplyConst(type.ordinal(), constReal, constImag, name));
    }

    /**
     * Create a MultiplyConst block with real constant and default name.
     * @param type     Data type to process
     * @param constant Real constant factor
     */
    public MultiplyConst(@NonNull DataType type, float constant) {
        this(type, constant, 0.0f, "multiply_const");
    }

    /**
     * Create a MultiplyConst block with complex constant and default name.
     * @param type      Data type to process
     * @param constReal Real part of the constant factor
     * @param constImag Imaginary part of the constant factor
     */
    public MultiplyConst(@NonNull DataType type, float constReal, float constImag) {
        this(type, constReal, constImag, "multiply_const");
    }

    /**
     * Create a MultiplyConst block with unity gain (1.0) and default name.
     * @param type Data type to process
     */
    public MultiplyConst(@NonNull DataType type) {
        this(type, 1.0f, 0.0f, "multiply_const");
    }

    /**
     * Set a new real constant factor.
     * @param constant New real constant
     */
    public void setConstant(float constant) {
        nativeSetConstant(nativeHandle, constant, 0.0f);
    }

    /**
     * Set a new complex constant factor.
     * @param constReal New real part
     * @param constImag New imaginary part
     */
    public void setConstant(float constReal, float constImag) {
        nativeSetConstant(nativeHandle, constReal, constImag);
    }

    private static native long nativeCreateMultiplyConst(int type, float constReal, float constImag, String name);
    private native void nativeSetConstant(long handle, float constReal, float constImag);
}
