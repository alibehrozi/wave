package com.github.alibehrozi.wave.microdsp.blocks.sources;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that generates periodic signals.
 */
public class SignalSource extends Block {

    /**
     * Supported signal types
     */
    public enum SignalType {
        SINE(0),
        SQUARE(1),
        TRIANGLE(2),
        SAWTOOTH(3);

        final int value;

        SignalType(int value) {
            this.value = value;
        }
    }

    /**
     * Create a new SignalSource
     * @param type       Data type to output (FLOAT or COMPLEX_FLOAT)
     * @param sampleRate Sample rate in Hz
     * @param frequency  Signal frequency in Hz
     * @param amplitude  Signal amplitude
     * @param signalType Type of signal to generate
     * @param name       Block name
     */
    public SignalSource(@NonNull DataType type,
                        double sampleRate,
                        double frequency,
                        double amplitude,
                        SignalType signalType,
                        String name) {
        super(name,
                nativeCreateSignalSource(type.ordinal(),
                        sampleRate,
                        frequency,
                        amplitude,
                        signalType.value,
                        name)
        );
    }

    /**
     * Create a new SignalSource with default name
     * @param type       Data type to output
     * @param sampleRate Sample rate in Hz
     * @param frequency  Signal frequency in Hz
     * @param amplitude  Signal amplitude
     * @param signalType Type of signal
     */
    public SignalSource(@NonNull DataType type, double sampleRate, double frequency, double amplitude,
                        SignalType signalType) {
        this(type, sampleRate, frequency, amplitude, signalType, "signal_source");
    }

    /**
     * Set signal frequency
     * @param frequency New frequency in Hz
     */
    public void setFrequency(double frequency) {
        nativeSetFrequency(nativeHandle, frequency);
    }

    /**
     * Set signal amplitude
     * @param amplitude New amplitude
     */
    public void setAmplitude(double amplitude) {
        nativeSetAmplitude(nativeHandle, amplitude);
    }

    /**
     * Set signal type
     * @param signalType New signal type
     */
    public void setSignalType(SignalType signalType) {
        nativeSetSignalType(nativeHandle, signalType.value);
    }

    // Native methods
    private static native long nativeCreateSignalSource(int type,
                                                        double sampleRate,
                                                        double frequency,
                                                        double amplitude,
                                                        int signalType,
                                                        String name);
    private native void nativeSetFrequency(long handle, double frequency);
    private native void nativeSetAmplitude(long handle, double amplitude);
    private native void nativeSetSignalType(long handle, int signalType);
}
