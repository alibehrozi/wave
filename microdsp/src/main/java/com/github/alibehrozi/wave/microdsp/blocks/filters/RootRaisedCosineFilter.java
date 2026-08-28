package com.github.alibehrozi.wave.microdsp.blocks.filters;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * Dedicated Finite Impulse Response (FIR) Root Raised Cosine (RRC) pulse shaping filter block.
 * Primarily used in digital communication systems (PSK, QAM) for matched filtering without ISI.
 */
public class RootRaisedCosineFilter extends FirFilter {

    private double gain;
    private double samplingFreq;
    private double symbolRate;
    private double excessBw;
    private int ntaps;

    /**
     * Create a RootRaisedCosineFilter.
     * @param type          Data type
     * @param gain          Filter gain
     * @param samplingFreq  Sampling frequency in Hz
     * @param symbolRate    Symbol rate in symbols/sec
     * @param excessBw      Excess bandwidth (rolloff factor, 0.0 to 1.0)
     * @param ntaps         Number of filter taps
     * @param decimation    Decimation factor
     * @param interpolation Interpolation factor
     * @param name          Block name
     */
    public RootRaisedCosineFilter(@NonNull DataType type,
                                  double gain,
                                  double samplingFreq,
                                  double symbolRate,
                                  double excessBw,
                                  int ntaps,
                                  int decimation,
                                  int interpolation,
                                  String name) {
        super(type,
                FilterDesign.rootRaisedCosine(gain, samplingFreq, symbolRate, excessBw, ntaps),
                decimation,
                interpolation,
                name);
        this.gain = gain;
        this.samplingFreq = samplingFreq;
        this.symbolRate = symbolRate;
        this.excessBw = excessBw;
        this.ntaps = ntaps;
    }

    /**
     * Create a RootRaisedCosineFilter with default parameters.
     * @param type         Data type
     * @param samplingFreq Sampling frequency in Hz
     * @param symbolRate   Symbol rate in symbols/sec
     * @param excessBw     Excess bandwidth
     * @param ntaps        Number of filter taps
     */
    public RootRaisedCosineFilter(@NonNull DataType type,
                                  double samplingFreq,
                                  double symbolRate,
                                  double excessBw,
                                  int ntaps) {
        this(type,
                1.0,
                samplingFreq,
                symbolRate,
                excessBw,
                ntaps,
                1,
                1,
                "rrc_filter");
    }

    /**
     * Get the symbol rate.
     * @return Symbol rate in symbols/sec
     */
    public double getSymbolRate() {
        return symbolRate;
    }

    /**
     * Get the excess bandwidth.
     * @return Excess bandwidth (rolloff factor)
     */
    public double getExcessBandwidth() {
        return excessBw;
    }

    /**
     * Get the sampling frequency.
     * @return Sampling frequency in Hz
     */
    public double getSamplingFrequency() {
        return samplingFreq;
    }
}
