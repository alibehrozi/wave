package com.github.alibehrozi.wave.microdsp.blocks;

import static org.junit.Assert.*;

import com.github.alibehrozi.wave.microdsp.blocks.demodulation.SsbDemodulator;
import com.github.alibehrozi.wave.microdsp.blocks.filters.FilterDesign;
import com.github.alibehrozi.wave.microdsp.blocks.modulation.SsbModulator;
import com.github.alibehrozi.wave.microdsp.blocks.sinks.AudioSink;
import com.github.alibehrozi.wave.microdsp.blocks.sinks.FileSink;
import com.github.alibehrozi.wave.microdsp.blocks.sources.AudioSource;
import com.github.alibehrozi.wave.microdsp.blocks.sources.NoiseSource;
import com.github.alibehrozi.wave.microdsp.blocks.sources.SignalSource;
import com.github.alibehrozi.wave.microdsp.core.DataType;

import org.junit.Test;

/**
 * Robust unit tests for DSP Blocks configurations, parameter validation, and
 * audio setup.
 */
public class BlocksUnitTest {

    @Test
    public void testAudioSourceConfigValidation() {
        AudioSource.AudioConfig config = new AudioSource.AudioConfig(48000, 2);
        assertEquals(48000, config.sampleRate);
        assertEquals(2, config.channels);
        assertEquals(1024, config.bufferSize);

        AudioSource.AudioConfig customConfig = new AudioSource.AudioConfig(44100, 1, 2048);
        assertEquals(44100, customConfig.sampleRate);
        assertEquals(1, customConfig.channels);
        assertEquals(2048, customConfig.bufferSize);

        try {
            new AudioSource.AudioConfig(0, 2);
            fail("Expected exception for sample rate <= 0");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource.AudioConfig(200000, 2);
            fail("Expected exception for sample rate > 192000");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource.AudioConfig(48000, 0);
            fail("Expected exception for channel count <= 0");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource.AudioConfig(48000, 9);
            fail("Expected exception for channel count > 8");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource.AudioConfig(48000, 2, 0);
            fail("Expected exception for buffer size <= 0");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testAudioSourceParameterValidation() {
        try {
            new AudioSource(null, 44100, 1, "test");
            fail("Expected exception for null DataType");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource(DataType.INT32, 44100, 1, "test");
            fail("Expected exception for unsupported DataType INT32");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource(DataType.FLOAT, 0, 1, "test");
            fail("Expected exception for sample rate <= 0");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource(DataType.FLOAT, 200000, 1, "test");
            fail("Expected exception for sample rate > 192000");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource(DataType.FLOAT, 44100, 0, "test");
            fail("Expected exception for channels <= 0");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource(DataType.FLOAT, 44100, 9, "test");
            fail("Expected exception for channels > 8");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSource(DataType.FLOAT, 44100, 1, "");
            fail("Expected exception for empty block name");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testAudioSinkConfigValidation() {
        AudioSink.AudioConfig config = new AudioSink.AudioConfig(48000, 2);
        assertEquals(48000, config.sampleRate);
        assertEquals(2, config.channels);
        assertEquals(1024, config.bufferSize);

        try {
            new AudioSink.AudioConfig(0, 2);
            fail("Expected exception for sample rate <= 0");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new AudioSink.AudioConfig(48000, 0);
            fail("Expected exception for channel count <= 0");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSignalSourceParameterValidation() {
        try {
            new SignalSource(null, 44100, 1000, 1.0, SignalSource.SignalType.SINE, "sig");
            fail("Expected exception for null DataType");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new SignalSource(DataType.SHORT, 44100, 1000, 1.0, SignalSource.SignalType.SINE, "sig");
            fail("Expected exception for unsupported DataType SHORT");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new SignalSource(DataType.FLOAT, -1, 1000, 1.0, SignalSource.SignalType.SINE, "sig");
            fail("Expected exception for negative sample rate");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new SignalSource(DataType.FLOAT, 44100, 1000, 1.0, null, "sig");
            fail("Expected exception for null SignalType");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new SignalSource(DataType.FLOAT, 44100, 1000, 1.0, SignalSource.SignalType.SINE, "");
            fail("Expected exception for empty block name");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testNoiseSourceParameterValidation() {
        try {
            new NoiseSource(null, 1.0f, NoiseSource.NoiseType.GAUSSIAN, "noise");
            fail("Expected exception for null DataType");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new NoiseSource(DataType.SHORT, 1.0f, NoiseSource.NoiseType.GAUSSIAN, "noise");
            fail("Expected exception for unsupported DataType SHORT");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new NoiseSource(DataType.FLOAT, 1.0f, null, "noise");
            fail("Expected exception for null NoiseType");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new NoiseSource(DataType.FLOAT, 1.0f, NoiseSource.NoiseType.GAUSSIAN, "  ");
            fail("Expected exception for blank block name");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testFileSinkParameterValidation() {
        try {
            new FileSink(null, "out.raw", FileSink.FileMode.OVERWRITE, "sink");
            fail("Expected exception for null DataType");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new FileSink(DataType.FLOAT, null, FileSink.FileMode.OVERWRITE, "sink");
            fail("Expected exception for null filename");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new FileSink(DataType.FLOAT, "", FileSink.FileMode.OVERWRITE, "sink");
            fail("Expected exception for empty filename");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new FileSink(DataType.FLOAT, "out.raw", null, "sink");
            fail("Expected exception for null FileMode");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new FileSink(DataType.FLOAT, "out.raw", FileSink.FileMode.OVERWRITE, null);
            fail("Expected exception for null block name");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSsbModulatorAndDemodulatorValidation() {
        try {
            new SsbModulator(null, 65, "ssb_mod");
            fail("Expected exception for null Sideband in modulator");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new SsbModulator(SsbModulator.Sideband.USB, 64, "ssb_mod");
            fail("Expected exception for even number of taps in modulator");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new SsbDemodulator(null, "ssb_demod");
            fail("Expected exception for null Sideband in demodulator");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new SsbDemodulator(SsbDemodulator.Sideband.LSB, "");
            fail("Expected exception for empty block name in demodulator");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testFilterDesignWindowTypes() {
        assertEquals(0, FilterDesign.WindowType.HAMMING.value);
        assertEquals(1, FilterDesign.WindowType.HANN.value);
        assertEquals(2, FilterDesign.WindowType.BLACKMAN.value);
        assertEquals(3, FilterDesign.WindowType.RECTANGULAR.value);
        assertEquals(4, FilterDesign.WindowType.BLACKMAN_HARRIS.value);
        assertEquals(5, FilterDesign.WindowType.BARTLETT.value);
        assertEquals(6, FilterDesign.WindowType.FLAT_TOP.value);
        assertEquals(7, FilterDesign.WindowType.KAISER.value);
    }

    @Test
    public void testSupportedDataTypes() {
        assertEquals(7, DataType.values().length);
        assertEquals(DataType.FLOAT, DataType.valueOf("FLOAT"));
        assertEquals(DataType.COMPLEX_FLOAT, DataType.valueOf("COMPLEX_FLOAT"));
        assertEquals(DataType.DOUBLE, DataType.valueOf("DOUBLE"));
        assertEquals(DataType.COMPLEX_DOUBLE, DataType.valueOf("COMPLEX_DOUBLE"));
        assertEquals(DataType.INT32, DataType.valueOf("INT32"));
        assertEquals(DataType.SHORT, DataType.valueOf("SHORT"));
        assertEquals(DataType.BYTE, DataType.valueOf("BYTE"));
    }

    @Test
    public void testDataTypeOrdinals() {
        for (DataType type : DataType.values()) {
            assertNotNull(type.name());
            assertTrue(type.ordinal() >= 0);
        }
    }
}
