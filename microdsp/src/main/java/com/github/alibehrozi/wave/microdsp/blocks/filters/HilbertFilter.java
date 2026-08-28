package com.github.alibehrozi.wave.microdsp.blocks.filters;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * DSP block that performs a Hilbert transform.
 * Converts a real signal (FLOAT) into an analytic signal (COMPLEX_FLOAT) with a 90-degree phase shift on Q.
 */
public class HilbertFilter extends Block {

    /**
     * Create a HilbertFilter.
     * @param ntaps Number of filter taps (should be odd)
     * @param name  Block name
     */
    public HilbertFilter(int ntaps, @NonNull String name) {
        super(name, nativeCreateHilbertFilter(ntaps, name));
    }

    /**
     * Create a HilbertFilter with default name.
     * @param ntaps Number of filter taps
     */
    public HilbertFilter(int ntaps) {
        this(ntaps, "hilbert_filter");
    }

    /**
     * Create a HilbertFilter with default parameters.
     */
    public HilbertFilter() {
        this(65, "hilbert_filter");
    }

    private static native long nativeCreateHilbertFilter(int ntaps, String name);
}
