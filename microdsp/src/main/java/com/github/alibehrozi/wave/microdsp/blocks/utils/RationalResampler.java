package com.github.alibehrozi.wave.microdsp.blocks.utils;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that performs rational resampling (interpolation by {@code L} followed by decimation by {@code M}).
 * <p>
 * Uses polyphase filter banks to perform anti-imaging and anti-aliasing filtering efficiently.
 */
public class RationalResampler extends Block {

    /**
     * Create a RationalResampler with custom parameters and name.
     * @param type          Data type to process (FLOAT or COMPLEX_FLOAT)
     * @param interpolation Interpolation factor (upsampling rate L &ge; 1)
     * @param decimation    Decimation factor (downsampling rate M &ge; 1)
     * @param taps          Anti-aliasing FIR filter coefficients
     * @param name          Block name
     */
    public RationalResampler(@NonNull DataType type,
                             int interpolation,
                             int decimation,
                             float[] taps,
                             String name) {
        super(name, nativeCreateRationalResampler(type.ordinal(), interpolation, decimation, taps, name));
    }

    /**
     * Create a RationalResampler with default name.
     * @param type          Data type to process
     * @param interpolation Interpolation factor (L)
     * @param decimation    Decimation factor (M)
     * @param taps          Anti-aliasing FIR filter coefficients
     */
    public RationalResampler(@NonNull DataType type, int interpolation, int decimation, float[] taps) {
        this(type, interpolation, decimation, taps, "rational_resampler");
    }

    private static native long nativeCreateRationalResampler(int type,
                                                             int interpolation,
                                                             int decimation,
                                                             float[] taps,
                                                             String name);
}