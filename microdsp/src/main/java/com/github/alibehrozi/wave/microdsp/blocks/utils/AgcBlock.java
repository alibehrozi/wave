package com.github.alibehrozi.wave.microdsp.blocks.utils;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * Automatic Gain Control (AGC) block.
 * <p>
 * Dynamically adjusts the gain of an incoming signal to maintain a constant average output power level.
 * Supports asymmetric attack (speeding up response to weak signals) and decay (preventing rapid gain drops).
 */
public class AgcBlock extends Block {

    /**
     * Create an AgcBlock with custom control parameters and name.
     * @param type        Data type to process (FLOAT or COMPLEX_FLOAT)
     * @param targetLevel Target output amplitude level (0.0 to 1.0)
     * @param attackRate  Rate at which gain increases when the input signal is weak
     * @param decayRate   Rate at which gain decreases when the input signal is strong
     * @param maxGain     Maximum allowable gain multiplier
     * @param name        Block name
     */
    public AgcBlock(@NonNull DataType type,
                    float targetLevel,
                    float attackRate,
                    float decayRate,
                    float maxGain,
                    String name) {
        super(name, nativeCreateAgcBlock(type.ordinal(), targetLevel, attackRate, decayRate, maxGain, name));
    }

    /**
     * Create an AgcBlock with default AGC parameters (target=0.5, attack=1e-3, decay=1e-4, maxGain=1000.0).
     * @param type Data type to process
     */
    public AgcBlock(@NonNull DataType type) {
        this(type, 0.5f, 1e-3f, 1e-4f, 1000.0f, "agc");
    }

    /**
     * Update the target output level dynamically at runtime.
     * @param level New target amplitude level (e.g., 0.5)
     */
    public void setTargetLevel(float level) {
        nativeSetTargetLevel(nativeHandle, level);
    }

    private static native long nativeCreateAgcBlock(int type,
                                                    float targetLevel,
                                                    float attackRate,
                                                    float decayRate,
                                                    float maxGain,
                                                    String name);
    private native void nativeSetTargetLevel(long handle, float level);
}
