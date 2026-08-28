package com.github.alibehrozi.wave.microdsp.blocks.utils;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * Power squelch block that gates the audio/IQ stream based on estimated signal power.
 * <p>
 * If average input power drops below the threshold, the output is muted (zeroed out)
 * to eliminate background noise when no transmission is received.
 */
public class SquelchBlock extends Block {

    /**
     * Create a SquelchBlock with custom threshold and name.
     * @param type        Data type to process (FLOAT or COMPLEX_FLOAT)
     * @param thresholdDb Power threshold in decibels (dB) below which signal is muted
     * @param name        Block name
     */
    public SquelchBlock(@NonNull DataType type, float thresholdDb, String name) {
        super(name, nativeCreateSquelchBlock(type.ordinal(), thresholdDb, name));
    }

    /**
     * Create a SquelchBlock with default name.
     * @param type        Data type to process
     * @param thresholdDb Power threshold in dB
     */
    public SquelchBlock(@NonNull DataType type, float thresholdDb) {
        this(type, thresholdDb, "squelch");
    }

    /**
     * Update the power squelch threshold dynamically at runtime.
     * @param db New threshold in decibels (e.g., -40.0 dB)
     */
    public void setThreshold(float db) {
        nativeSetThreshold(nativeHandle, db);
    }

    private static native long nativeCreateSquelchBlock(int type, float thresholdDb, String name);
    private native void nativeSetThreshold(long handle, float db);
}
