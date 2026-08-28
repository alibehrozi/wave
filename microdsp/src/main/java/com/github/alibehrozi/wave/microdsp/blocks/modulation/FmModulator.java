package com.github.alibehrozi.wave.microdsp.blocks.modulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Frequency Modulation (FM) block.
 * <p>
 * Takes a {@code FLOAT} audio signal as input and produces a
 * {@code COMPLEX_FLOAT} IQ signal modulated as exp(j * integral(sensitivity * audio)).
 */
public class FmModulator extends Block {

    /**
     * Create an FmModulator with sensitivity and custom name.
     * @param sensitivity Modulation sensitivity (frequency deviation factor)
     * @param name        Block name
     */
    public FmModulator(float sensitivity, String name) {
        super(name, nativeCreateFmModulator(sensitivity, name));
    }

    /**
     * Create an FmModulator with default name.
     * @param sensitivity Modulation sensitivity (frequency deviation factor)
     */
    public FmModulator(float sensitivity) {
        this(sensitivity, "fm_mod");
    }

    /**
     * Set the modulation sensitivity.
     * @param sensitivity New sensitivity value
     */
    public void setSensitivity(float sensitivity) {
        nativeSetSensitivity(nativeHandle, sensitivity);
    }

    private static native long nativeCreateFmModulator(float sensitivity, String name);
    private native void nativeSetSensitivity(long handle, float sensitivity);
}
