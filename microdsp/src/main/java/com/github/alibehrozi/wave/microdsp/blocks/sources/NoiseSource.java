package com.github.alibehrozi.wave.microdsp.blocks.sources;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that generates random noise.
 */
public class NoiseSource extends Block {

    /**
     * Supported noise types
     */
    public enum NoiseType {
        GAUSSIAN(0),
        UNIFORM(1);

        final int value;

        NoiseType(int value) {
            this.value = value;
        }
    }

    /**
     * Create a new NoiseSource
     * @param type      Data type to output (FLOAT or COMPLEX_FLOAT)
     * @param amplitude Noise amplitude
     * @param noiseType Type of noise to generate
     * @param name      Block name
     */
    public NoiseSource(@NonNull DataType type, float amplitude, NoiseType noiseType, String name) {
        super(name, nativeCreateNoiseSource(type.ordinal(), amplitude, noiseType.value, name));
    }

    /**
     * Create a new NoiseSource with default name
     * @param type      Data type to output
     * @param amplitude Noise amplitude
     * @param noiseType Type of noise
     */
    public NoiseSource(@NonNull DataType type, float amplitude, NoiseType noiseType) {
        this(type, amplitude, noiseType, "noise_source");
    }

    /**
     * Set noise amplitude
     * @param amplitude New amplitude
     */
    public void setAmplitude(float amplitude) {
        nativeSetAmplitude(nativeHandle, amplitude);
    }

    /**
     * Set noise type
     * @param noiseType New noise type
     */
    public void setNoiseType(NoiseType noiseType) {
        nativeSetNoiseType(nativeHandle, noiseType.value);
    }

    // Native methods
    private static native long nativeCreateNoiseSource(int type, float amplitude, int noiseType, String name);
    private native void nativeSetAmplitude(long handle, float amplitude);
    private native void nativeSetNoiseType(long handle, int noiseType);
}
