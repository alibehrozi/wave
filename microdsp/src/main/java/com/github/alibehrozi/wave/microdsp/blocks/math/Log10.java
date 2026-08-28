package com.github.alibehrozi.wave.microdsp.blocks.math;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that computes scaled base-10 logarithm with offset:
 * <p>
 * Output: {@code out = n * log10(max(in, eps)) + k}
 * <p>
 * Commonly used for decibel (dB) scaling:
 * <ul>
 *   <li>Power to dB: n = 10.0, k = 0.0</li>
 *   <li>Amplitude to dB: n = 20.0, k = 0.0</li>
 * </ul>
 */
public class Log10 extends Block {

    /**
     * Create a Log10 block with custom scale factor, offset, epsilon, and name.
     * @param type Data type to process
     * @param n    Multiplication scaling factor (e.g., 10.0 for power, 20.0 for amplitude)
     * @param k    Additive offset factor
     * @param eps  Minimum input threshold to avoid taking log of zero/negative numbers
     * @param name Block name
     */
    public Log10(@NonNull DataType type, float n, float k, float eps, @NonNull String name) {
        super(name, nativeCreateLog10(type.ordinal(), n, k, eps, name));
    }

    /**
     * Create a Log10 block with default epsilon (1e-12) and custom name.
     * @param type Data type to process
     * @param n    Multiplication scaling factor
     * @param k    Additive offset factor
     * @param name Block name
     */
    public Log10(@NonNull DataType type, float n, float k, @NonNull String name) {
        this(type, n, k, 1e-12f, name);
    }

    /**
     * Create a Log10 block with default epsilon (1e-12) and default name.
     * @param type Data type to process
     * @param n    Multiplication scaling factor
     * @param k    Additive offset factor
     */
    public Log10(@NonNull DataType type, float n, float k) {
        this(type, n, k, 1e-12f, "log10");
    }

    /**
     * Create a simple Log10 block with n=1.0, k=0.0, default epsilon, and custom name.
     * @param type Data type to process
     * @param name Block name
     */
    public Log10(@NonNull DataType type, @NonNull String name) {
        this(type, 1.0f, 0.0f, 1e-12f, name);
    }

    /**
     * Create a simple Log10 block with default parameters.
     * @param type Data type to process
     */
    public Log10(@NonNull DataType type) {
        this(type, 1.0f, 0.0f, 1e-12f, "log10");
    }

    /**
     * Set new scaling and offset parameters dynamically at runtime.
     * @param n New multiplication scaling factor
     * @param k New additive offset factor
     */
    public void setParameters(float n, float k) {
        nativeSetParameters(nativeHandle, n, k);
    }

    private static native long nativeCreateLog10(int type, float n, float k, float eps, String name);

    private native void nativeSetParameters(long handle, float n, float k);
}
