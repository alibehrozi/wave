package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that adds a constant scalar or complex offset to the input stream.
 * <p>
 * Output: {@code out = in + constant}
 */
public class AddConst extends Block {

    /**
     * Create an AddConst block for real constant.
     * @param type     Data type to process
     * @param constant Real constant to add
     * @param name     Block name
     */
    public AddConst(@NonNull DataType type, float constant, @NonNull String name) {
        this(type, constant, 0.0f, name);
    }

    /**
     * Create an AddConst block for complex constant.
     * @param type      Data type to process
     * @param constReal Real part of the constant
     * @param constImag Imaginary part of the constant
     * @param name      Block name
     */
    public AddConst(@NonNull DataType type, float constReal, float constImag, @NonNull String name) {
        super(name, nativeCreateAddConst(type.ordinal(), constReal, constImag, name));
    }

    /**
     * Create an AddConst block with real constant and default name.
     * @param type     Data type to process
     * @param constant Real constant to add
     */
    public AddConst(@NonNull DataType type, float constant) {
        this(type, constant, 0.0f, "add_const");
    }

    /**
     * Create an AddConst block with complex constant and default name.
     * @param type      Data type to process
     * @param constReal Real part of the constant
     * @param constImag Imaginary part of the constant
     */
    public AddConst(@NonNull DataType type, float constReal, float constImag) {
        this(type, constReal, constImag, "add_const");
    }

    /**
     * Create an AddConst block with zero constant and default name.
     * @param type Data type to process
     */
    public AddConst(@NonNull DataType type) {
        this(type, 0.0f, 0.0f, "add_const");
    }

    /**
     * Set a new real constant.
     * @param constant New real constant
     */
    public void setConstant(float constant) {
        nativeSetConstant(nativeHandle, constant, 0.0f);
    }

    /**
     * Set a new complex constant.
     * @param constReal New real part
     * @param constImag New imaginary part
     */
    public void setConstant(float constReal, float constImag) {
        nativeSetConstant(nativeHandle, constReal, constImag);
    }

    private static native long nativeCreateAddConst(int type, float constReal, float constImag, String name);
    private native void nativeSetConstant(long handle, float constReal, float constImag);
}
