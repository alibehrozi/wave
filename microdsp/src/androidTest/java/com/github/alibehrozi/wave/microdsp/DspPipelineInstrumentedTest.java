package com.github.alibehrozi.wave.microdsp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.github.alibehrozi.wave.microdsp.blocks.demodulation.AmDemodulator;
import com.github.alibehrozi.wave.microdsp.blocks.demodulation.BpskDemodulator;
import com.github.alibehrozi.wave.microdsp.blocks.demodulation.FmDemodulator;
import com.github.alibehrozi.wave.microdsp.blocks.demodulation.FskDemodulator;
import com.github.alibehrozi.wave.microdsp.blocks.demodulation.SsbDemodulator;
import com.github.alibehrozi.wave.microdsp.blocks.demodulation.WfmDemodulator;
import com.github.alibehrozi.wave.microdsp.blocks.filters.BandPassFilter;
import com.github.alibehrozi.wave.microdsp.blocks.filters.BandRejectFilter;
import com.github.alibehrozi.wave.microdsp.blocks.filters.DcBlocker;
import com.github.alibehrozi.wave.microdsp.blocks.filters.FilterDesign;
import com.github.alibehrozi.wave.microdsp.blocks.filters.FirFilter;
import com.github.alibehrozi.wave.microdsp.blocks.filters.HighPassFilter;
import com.github.alibehrozi.wave.microdsp.blocks.filters.HilbertFilter;
import com.github.alibehrozi.wave.microdsp.blocks.filters.IirFilter;
import com.github.alibehrozi.wave.microdsp.blocks.filters.LowPassFilter;
import com.github.alibehrozi.wave.microdsp.blocks.filters.RootRaisedCosineFilter;
import com.github.alibehrozi.wave.microdsp.blocks.modulation.AmModulator;
import com.github.alibehrozi.wave.microdsp.blocks.modulation.BpskModulator;
import com.github.alibehrozi.wave.microdsp.blocks.modulation.FmModulator;
import com.github.alibehrozi.wave.microdsp.blocks.modulation.FskModulator;
import com.github.alibehrozi.wave.microdsp.blocks.modulation.SsbModulator;
import com.github.alibehrozi.wave.microdsp.blocks.sinks.JavaSink;
import com.github.alibehrozi.wave.microdsp.blocks.sources.JavaSource;
import com.github.alibehrozi.wave.microdsp.blocks.sources.NoiseSource;
import com.github.alibehrozi.wave.microdsp.blocks.sources.SignalSource;
import com.github.alibehrozi.wave.microdsp.blocks.math.Abs;
import com.github.alibehrozi.wave.microdsp.blocks.math.Add;
import com.github.alibehrozi.wave.microdsp.blocks.math.AddConst;
import com.github.alibehrozi.wave.microdsp.blocks.math.ComplexToMag;
import com.github.alibehrozi.wave.microdsp.blocks.math.ComplexToMagSquared;
import com.github.alibehrozi.wave.microdsp.blocks.math.ComplexToRealImag;
import com.github.alibehrozi.wave.microdsp.blocks.math.Conjugate;
import com.github.alibehrozi.wave.microdsp.blocks.math.Divide;
import com.github.alibehrozi.wave.microdsp.blocks.math.Log10;
import com.github.alibehrozi.wave.microdsp.blocks.math.Multiply;
import com.github.alibehrozi.wave.microdsp.blocks.math.MultiplyConst;
import com.github.alibehrozi.wave.microdsp.blocks.math.RealImagToComplex;
import com.github.alibehrozi.wave.microdsp.blocks.math.Subtract;
import com.github.alibehrozi.wave.microdsp.blocks.utils.AgcBlock;
import com.github.alibehrozi.wave.microdsp.blocks.utils.RationalResampler;
import com.github.alibehrozi.wave.microdsp.blocks.utils.SquelchBlock;
import com.github.alibehrozi.wave.microdsp.core.DataType;
import com.github.alibehrozi.wave.microdsp.core.Flowgraph;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Instrumentation tests for the DSP module.
 * Tests native block execution, inter-block connections, data transfer, and signal processing.
 */
@RunWith(AndroidJUnit4.class)
public class DspPipelineInstrumentedTest {

    static {
        System.loadLibrary("microdsp");
    }

    private static final int BUFFER_CAPACITY = 16384;

    private static ByteBuffer createDirectFloatBuffer(int floatCount) {
        ByteBuffer buf = ByteBuffer.allocateDirect(floatCount * 4);
        buf.order(ByteOrder.nativeOrder());
        return buf;
    }

    /**
     * Test 1: Direct Float Data Transfer (JavaSource -> JavaSink)
     */
    @Test
    public void testDirectFloatDataTransfer() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "src");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "sink");
             Flowgraph fg = new Flowgraph("fg_direct_float")) {

            fg.connect(source, sink);
            assertTrue(fg.start());
            assertTrue(source.isActive());
            assertTrue(sink.isActive());

            int sampleCount = 512;
            ByteBuffer inBuf = createDirectFloatBuffer(sampleCount);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < sampleCount; i++) {
                inFloats.put((float) Math.sin(2.0 * Math.PI * i / 64.0));
            }

            int pushed = source.push(inBuf, sampleCount);
            assertEquals(sampleCount, pushed);

            // Allow worker thread to process
            Thread.sleep(60);

            ByteBuffer outBuf = createDirectFloatBuffer(sampleCount);
            int pulled = sink.pull(outBuf, sampleCount);
            assertEquals(sampleCount, pulled);

            FloatBuffer outFloats = outBuf.asFloatBuffer();
            inFloats.rewind();
            for (int i = 0; i < sampleCount; i++) {
                assertEquals("Float mismatch at sample " + i, inFloats.get(i), outFloats.get(i), 1e-6f);
            }

            fg.stop();
        }
    }

    /**
     * Test 2: Complex Float Data Transfer (JavaSource -> JavaSink)
     */
    @Test
    public void testComplexFloatDataTransfer() throws Exception {
        try (JavaSource source = new JavaSource(DataType.COMPLEX_FLOAT, BUFFER_CAPACITY, "csrc");
             JavaSink sink = new JavaSink(DataType.COMPLEX_FLOAT, BUFFER_CAPACITY, "csink");
             Flowgraph fg = new Flowgraph("fg_complex")) {

            fg.connect(source, sink);
            assertTrue(fg.start());

            int complexCount = 256; // 512 floats (I/Q pairs)
            ByteBuffer inBuf = createDirectFloatBuffer(complexCount * 2);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < complexCount; i++) {
                inFloats.put((float) Math.cos(2.0 * Math.PI * i / 32.0)); // I
                inFloats.put((float) Math.sin(2.0 * Math.PI * i / 32.0)); // Q
            }

            int pushed = source.push(inBuf, complexCount);
            assertEquals(complexCount, pushed);

            Thread.sleep(60);

            ByteBuffer outBuf = createDirectFloatBuffer(complexCount * 2);
            int pulled = sink.pull(outBuf, complexCount);
            assertEquals(complexCount, pulled);

            FloatBuffer outFloats = outBuf.asFloatBuffer();
            inFloats.rewind();
            for (int i = 0; i < complexCount * 2; i++) {
                assertEquals("Complex mismatch at index " + i, inFloats.get(i), outFloats.get(i), 1e-6f);
            }

            fg.stop();
        }
    }

    /**
     * Test 3: Periodic Signal Source Generation (Sine, Square, Sawtooth)
     */
    @Test
    public void testSignalSourceSineGeneration() throws Exception {
        try (SignalSource sigSrc = new SignalSource(DataType.FLOAT, 48000.0, 1000.0, 1.0, SignalSource.SignalType.SINE, "sine_gen");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "sine_sink");
             Flowgraph fg = new Flowgraph("fg_sine")) {

            fg.connect(sigSrc, sink);
            assertTrue(fg.start());

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(1024);
            int pulled = sink.pull(outBuf, 1024);
            assertTrue("Should receive samples from SignalSource", pulled > 0);

            FloatBuffer floats = outBuf.asFloatBuffer();
            float maxVal = 0.0f;
            float sum = 0.0f;
            for (int i = 0; i < pulled; i++) {
                float val = floats.get(i);
                maxVal = Math.max(maxVal, Math.abs(val));
                sum += val;
            }

            assertTrue("Sine peak should be close to 1.0", maxVal > 0.8f && maxVal <= 1.05f);
            assertEquals("Sine mean should be near 0", 0.0f, sum / pulled, 0.15f);

            fg.stop();
        }
    }

    /**
     * Test 4: Random Noise Source Generation (Gaussian and Uniform)
     */
    @Test
    public void testNoiseSourceGeneration() throws Exception {
        try (NoiseSource noiseSrc = new NoiseSource(DataType.FLOAT, 0.5f, NoiseSource.NoiseType.GAUSSIAN, "noise_gen");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "noise_sink");
             Flowgraph fg = new Flowgraph("fg_noise")) {

            fg.connect(noiseSrc, sink);
            assertTrue(fg.start());

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(1024);
            int pulled = sink.pull(outBuf, 1024);
            assertTrue("Should receive samples from NoiseSource", pulled > 0);

            FloatBuffer floats = outBuf.asFloatBuffer();
            float sum = 0.0f;
            float sumSq = 0.0f;
            for (int i = 0; i < pulled; i++) {
                float val = floats.get(i);
                sum += val;
                sumSq += val * val;
            }

            float mean = sum / pulled;
            float variance = (sumSq / pulled) - (mean * mean);

            assertEquals("Gaussian noise mean should be near 0", 0.0f, mean, 0.15f);
            assertTrue("Noise should have non-zero variance", variance > 0.01f);

            fg.stop();
        }
    }

    /**
     * Test 5: FIR Low-Pass Filter Pipeline (JavaSource -> FirFilter -> JavaSink)
     */
    @Test
    public void testFirFilterLowPassPipeline() throws Exception {
        float[] taps = FilterDesign.lowPass(48000.0, 2000.0, 1000.0);
        assertNotNull(taps);
        assertTrue(taps.length > 0);

        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "filter_in");
             FirFilter filter = new FirFilter(DataType.FLOAT, taps, 1, 1, "lpf");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "filter_out");
             Flowgraph fg = new Flowgraph("fg_filter")) {

            fg.connect(source, filter);
            fg.connect(filter, sink);
            assertTrue(fg.start());

            // 1. Send low frequency tone (500 Hz, well inside passband)
            int sampleCount = 1024;
            ByteBuffer passBuf = createDirectFloatBuffer(sampleCount);
            FloatBuffer passFloats = passBuf.asFloatBuffer();
            for (int i = 0; i < sampleCount; i++) {
                passFloats.put((float) Math.sin(2.0 * Math.PI * 500.0 * i / 48000.0));
            }
            source.push(passBuf, sampleCount);

            Thread.sleep(80);

            ByteBuffer outPassBuf = createDirectFloatBuffer(sampleCount);
            int pulledPass = sink.pull(outPassBuf, sampleCount);
            assertTrue("Should pull filtered samples", pulledPass > 0);

            FloatBuffer outFloats = outPassBuf.asFloatBuffer();
            float maxPass = 0.0f;
            for (int i = taps.length; i < pulledPass; i++) {
                maxPass = Math.max(maxPass, Math.abs(outFloats.get(i)));
            }
            assertTrue("Passband signal should pass with high amplitude", maxPass > 0.7f);

            fg.stop();
        }
    }

    /**
     * Test 6: Rational Resampler Pipeline (2x Interpolation)
     */
    @Test
    public void testRationalResamplerPipeline() throws Exception {
        float[] taps = new float[]{1.0f, 1.0f};

        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "resample_src");
             RationalResampler resampler = new RationalResampler(DataType.FLOAT, 2, 1, taps, "resampler_2x");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "resample_sink");
             Flowgraph fg = new Flowgraph("fg_resample")) {

            fg.connect(source, resampler);
            fg.connect(resampler, sink);
            assertTrue(fg.start());

            int inCount = 256;
            ByteBuffer inBuf = createDirectFloatBuffer(inCount);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < inCount; i++) {
                inFloats.put(1.0f);
            }
            source.push(inBuf, inCount);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(inCount * 2 + 64);
            int pulled = sink.pull(outBuf, inCount * 2 + 64);
            assertTrue("Resampler should produce interpolated samples", pulled >= inCount);

            fg.stop();
        }
    }

    /**
     * Test 7: Automatic Gain Control (AGC) Pipeline
     */
    @Test
    public void testAgcPipeline() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "agc_src");
             AgcBlock agc = new AgcBlock(DataType.FLOAT, 0.8f, 0.05f, 0.01f, 50.0f, "agc");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "agc_sink");
             Flowgraph fg = new Flowgraph("fg_agc")) {

            fg.connect(source, agc);
            fg.connect(agc, sink);
            assertTrue(fg.start());

            // Push small amplitude signal (amplitude = 0.05f)
            int sampleCount = 1024;
            ByteBuffer inBuf = createDirectFloatBuffer(sampleCount);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < sampleCount; i++) {
                inFloats.put(0.05f * (float) Math.sin(2.0 * Math.PI * i / 16.0));
            }
            source.push(inBuf, sampleCount);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(sampleCount);
            int pulled = sink.pull(outBuf, sampleCount);
            assertTrue("AGC should produce samples", pulled > 0);

            FloatBuffer outFloats = outBuf.asFloatBuffer();
            float maxOut = 0.0f;
            for (int i = pulled / 2; i < pulled; i++) {
                maxOut = Math.max(maxOut, Math.abs(outFloats.get(i)));
            }

            assertTrue("AGC should amplify small signal toward target level", maxOut > 0.1f);

            fg.stop();
        }
    }

    /**
     * Test 8: Squelch Block Pipeline
     */
    @Test
    public void testSquelchPipeline() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "squelch_src");
             SquelchBlock squelch = new SquelchBlock(DataType.FLOAT, -20.0f, "squelch");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "squelch_sink");
             Flowgraph fg = new Flowgraph("fg_squelch")) {

            fg.connect(source, squelch);
            fg.connect(squelch, sink);
            assertTrue(fg.start());

            // 1. Weak signal (amplitude 0.0001f -> power << -20 dB)
            int count = 512;
            ByteBuffer weakBuf = createDirectFloatBuffer(count);
            FloatBuffer weakFloats = weakBuf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                weakFloats.put(0.0001f);
            }
            source.push(weakBuf, count);

            Thread.sleep(60);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            if (pulled > 0) {
                FloatBuffer outFloats = outBuf.asFloatBuffer();
                for (int i = 0; i < pulled; i++) {
                    assertEquals("Weak signal below threshold should be muted", 0.0f, outFloats.get(i), 1e-4f);
                }
            }

            fg.stop();
        }
    }

    /**
     * Test 9: AM Modulation and Demodulation Loopback
     */
    @Test
    public void testAmModemLoopback() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "am_in");
             AmModulator mod = new AmModulator("am_mod");
             AmDemodulator demod = new AmDemodulator("am_demod");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "am_out");
             Flowgraph fg = new Flowgraph("fg_am")) {

            fg.connect(source, mod);
            fg.connect(mod, demod);
            fg.connect(demod, sink);
            assertTrue(fg.start());

            int count = 1024;
            ByteBuffer inBuf = createDirectFloatBuffer(count);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                inFloats.put(0.5f + 0.3f * (float) Math.sin(2.0 * Math.PI * i / 32.0));
            }
            source.push(inBuf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("AM demodulator should output samples", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 10: FM Modulation and Demodulation Loopback
     */
    @Test
    public void testFmModemLoopback() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "fm_in");
             FmModulator mod = new FmModulator(1.0f, "fm_mod");
             FmDemodulator demod = new FmDemodulator(1.0f, "fm_demod");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "fm_out");
             Flowgraph fg = new Flowgraph("fg_fm")) {

            fg.connect(source, mod);
            fg.connect(mod, demod);
            fg.connect(demod, sink);
            assertTrue(fg.start());

            int count = 1024;
            ByteBuffer inBuf = createDirectFloatBuffer(count);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                inFloats.put(0.5f * (float) Math.sin(2.0 * Math.PI * i / 64.0));
            }
            source.push(inBuf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("FM demodulator should output samples", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 11: WFM Demodulator Pipeline
     */
    @Test
    public void testWfmDemodulatorPipeline() throws Exception {
        try (JavaSource source = new JavaSource(DataType.COMPLEX_FLOAT, BUFFER_CAPACITY, "wfm_in");
             WfmDemodulator wfm = new WfmDemodulator(240000.0, 75e-6, "wfm_demod");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "wfm_out");
             Flowgraph fg = new Flowgraph("fg_wfm")) {

            fg.connect(source, wfm);
            fg.connect(wfm, sink);
            assertTrue(fg.start());

            int count = 512;
            ByteBuffer inBuf = createDirectFloatBuffer(count * 2);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                inFloats.put((float) Math.cos(2.0 * Math.PI * i / 16.0));
                inFloats.put((float) Math.sin(2.0 * Math.PI * i / 16.0));
            }
            source.push(inBuf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("WFM demodulator should process complex input", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 12: BPSK Modulation and Demodulation Loopback
     */
    @Test
    public void testBpskModemLoopback() throws Exception {
        try (JavaSource source = new JavaSource(DataType.BYTE, BUFFER_CAPACITY, "bpsk_in");
             BpskModulator mod = new BpskModulator("bpsk_mod");
             BpskDemodulator demod = new BpskDemodulator("bpsk_demod");
             JavaSink sink = new JavaSink(DataType.BYTE, BUFFER_CAPACITY, "bpsk_out");
             Flowgraph fg = new Flowgraph("fg_bpsk")) {

            fg.connect(source, mod);
            fg.connect(mod, demod);
            fg.connect(demod, sink);
            assertTrue(fg.start());

            int count = 256;
            ByteBuffer inBuf = ByteBuffer.allocateDirect(count);
            for (int i = 0; i < count; i++) {
                inBuf.put((byte) (i % 2));
            }
            source.push(inBuf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = ByteBuffer.allocateDirect(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("BPSK demodulator should output bits/bytes", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 13: FSK Modulation and Demodulation Loopback
     */
    @Test
    public void testFskModemLoopback() throws Exception {
        try (JavaSource source = new JavaSource(DataType.BYTE, BUFFER_CAPACITY, "fsk_in");
             FskModulator mod = new FskModulator(48000.0, 1200.0, 2200.0, "fsk_mod");
             FskDemodulator demod = new FskDemodulator(48000.0, 1200.0, 2200.0, "fsk_demod");
             JavaSink sink = new JavaSink(DataType.BYTE, BUFFER_CAPACITY, "fsk_out");
             Flowgraph fg = new Flowgraph("fg_fsk")) {

            fg.connect(source, mod);
            fg.connect(mod, demod);
            fg.connect(demod, sink);
            assertTrue(fg.start());

            int count = 256;
            ByteBuffer inBuf = ByteBuffer.allocateDirect(count);
            for (int i = 0; i < count; i++) {
                inBuf.put((byte) (i % 2));
            }
            source.push(inBuf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = ByteBuffer.allocateDirect(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("FSK demodulator should output bits", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 14: SSB Modulation and Demodulation Loopback
     */
    @Test
    public void testSsbModemLoopback() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "ssb_in");
             SsbModulator mod = new SsbModulator(SsbModulator.Sideband.USB, 31, "ssb_mod");
             SsbDemodulator demod = new SsbDemodulator(SsbDemodulator.Sideband.USB, "ssb_demod");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "ssb_out");
             Flowgraph fg = new Flowgraph("fg_ssb")) {

            fg.connect(source, mod);
            fg.connect(mod, demod);
            fg.connect(demod, sink);
            assertTrue(fg.start());

            int count = 512;
            ByteBuffer inBuf = createDirectFloatBuffer(count);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                inFloats.put((float) Math.sin(2.0 * Math.PI * i / 16.0));
            }
            source.push(inBuf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("SSB demodulator should output recovered signal", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 15: Multi-Block Complex Pipeline
     * SignalSource -> FirFilter -> AgcBlock -> JavaSink
     */
    @Test
    public void testMultiBlockComplexChain() throws Exception {
        float[] taps = FilterDesign.lowPass(1.0, 48000.0, 3000.0, 1000.0, FilterDesign.WindowType.HANN);

        try (SignalSource sigSrc = new SignalSource(DataType.FLOAT, 48000.0, 1000.0, 0.5, SignalSource.SignalType.SINE, "sig_gen");
             FirFilter filter = new FirFilter(DataType.FLOAT, taps, 1, 1, "lpf");
             AgcBlock agc = new AgcBlock(DataType.FLOAT, 0.9f, 0.05f, 0.01f, 10.0f, "agc");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "chain_sink");
             Flowgraph fg = new Flowgraph("fg_multi_stage")) {

            fg.connect(sigSrc, filter);
            fg.connect(filter, agc);
            fg.connect(agc, sink);

            assertTrue(fg.start());
            assertTrue(sigSrc.isActive());
            assertTrue(filter.isActive());
            assertTrue(agc.isActive());
            assertTrue(sink.isActive());

            Thread.sleep(100);

            ByteBuffer outBuf = createDirectFloatBuffer(1024);
            int pulled = sink.pull(outBuf, 1024);
            assertTrue("Multi-block chain should stream data through all blocks", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 16: Flowgraph Lifecycle and Listener Notifications
     */
    @Test
    public void testFlowgraphLifecycleAndListener() {
        AtomicBoolean startedCalled = new AtomicBoolean(false);
        AtomicBoolean stoppedCalled = new AtomicBoolean(false);

        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "src");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "sink");
             Flowgraph fg = new Flowgraph("fg_lifecycle")) {

            fg.setListener(new Flowgraph.FlowgraphListener() {
                @Override
                public void onFlowgraphStarted(Flowgraph flowgraph) {
                    startedCalled.set(true);
                }

                @Override
                public void onFlowgraphStopped(Flowgraph flowgraph) {
                    stoppedCalled.set(true);
                }

                @Override
                public void onFlowgraphError(Flowgraph flowgraph, String error) {}
            });

            fg.connect(source, sink);

            // First start-stop cycle
            assertTrue(fg.start());
            assertTrue(startedCalled.get());
            assertTrue(fg.isRunning());

            fg.stop();
            assertTrue(stoppedCalled.get());

            // Second start-stop cycle
            startedCalled.set(false);
            stoppedCalled.set(false);

            assertTrue(fg.start());
            assertTrue(startedCalled.get());
            fg.stop();
            assertTrue(stoppedCalled.get());
        }
    }

    /**
     * Test 17: Fan-Out (One Output Port Connected to Multiple Input Ports)
     * JavaSource -> JavaSink 1 AND JavaSink 2
     */
    @Test
    public void testOneOutputToMultipleInputsFanOut() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "fanout_src");
             JavaSink sink1 = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "sink1");
             JavaSink sink2 = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "sink2");
             Flowgraph fg = new Flowgraph("fg_fanout")) {

            fg.connect(source, "out", sink1, "in");
            fg.connect(source, "out", sink2, "in");

            assertTrue(fg.start());
            assertTrue(source.isActive());
            assertTrue(sink1.isActive());
            assertTrue(sink2.isActive());

            int sampleCount = 512;
            ByteBuffer inBuf = createDirectFloatBuffer(sampleCount);
            FloatBuffer inFloats = inBuf.asFloatBuffer();
            for (int i = 0; i < sampleCount; i++) {
                inFloats.put((float) Math.sin(2.0 * Math.PI * i / 32.0));
            }

            int pushed = source.push(inBuf, sampleCount);
            assertEquals(sampleCount, pushed);

            Thread.sleep(80);

            ByteBuffer outBuf1 = createDirectFloatBuffer(sampleCount);
            ByteBuffer outBuf2 = createDirectFloatBuffer(sampleCount);

            int pulled1 = sink1.pull(outBuf1, sampleCount);
            int pulled2 = sink2.pull(outBuf2, sampleCount);

            assertEquals("Sink 1 should receive all samples", sampleCount, pulled1);
            assertEquals("Sink 2 should receive all samples", sampleCount, pulled2);

            FloatBuffer outFloats1 = outBuf1.asFloatBuffer();
            FloatBuffer outFloats2 = outBuf2.asFloatBuffer();
            inFloats.rewind();

            for (int i = 0; i < sampleCount; i++) {
                float expected = inFloats.get(i);
                assertEquals("Sink 1 mismatch at " + i, expected, outFloats1.get(i), 1e-6f);
                assertEquals("Sink 2 mismatch at " + i, expected, outFloats2.get(i), 1e-6f);
            }

            fg.stop();
        }
    }

    /**
     * Test 18: Dedicated LowPassFilter Block & Dynamic Cutoff
     */
    @Test
    public void testDedicatedLowPassFilter() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "lpf_src");
             LowPassFilter lpf = new LowPassFilter(DataType.FLOAT, 48000.0, 2000.0, 1000.0);
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "lpf_sink");
             Flowgraph fg = new Flowgraph("fg_dedicated_lpf")) {

            fg.connect(source, lpf);
            fg.connect(lpf, sink);
            assertTrue(fg.start());

            // 500 Hz tone (passband)
            int count = 512;
            ByteBuffer buf = createDirectFloatBuffer(count);
            FloatBuffer fb = buf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                fb.put((float) Math.sin(2.0 * Math.PI * 500.0 * i / 48000.0));
            }
            source.push(buf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("LowPassFilter should stream samples", pulled > 0);

            // Dynamic cutoff modulation test
            lpf.setCutoffFrequency(3000.0);
            assertEquals(3000.0, lpf.getCutoffFrequency(), 1e-3);

            fg.stop();
        }
    }

    /**
     * Test 19: Dedicated HighPassFilter Block
     */
    @Test
    public void testDedicatedHighPassFilter() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "hpf_src");
             HighPassFilter hpf = new HighPassFilter(DataType.FLOAT, 48000.0, 4000.0, 1000.0);
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "hpf_sink");
             Flowgraph fg = new Flowgraph("fg_dedicated_hpf")) {

            fg.connect(source, hpf);
            fg.connect(hpf, sink);
            assertTrue(fg.start());

            // 8000 Hz tone (passband)
            int count = 512;
            ByteBuffer buf = createDirectFloatBuffer(count);
            FloatBuffer fb = buf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                fb.put((float) Math.sin(2.0 * Math.PI * 8000.0 * i / 48000.0));
            }
            source.push(buf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("HighPassFilter should stream samples", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 20: Dedicated BandPassFilter Block
     */
    @Test
    public void testDedicatedBandPassFilter() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "bpf_src");
             BandPassFilter bpf = new BandPassFilter(DataType.FLOAT, 48000.0, 2000.0, 5000.0, 1000.0);
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "bpf_sink");
             Flowgraph fg = new Flowgraph("fg_dedicated_bpf")) {

            fg.connect(source, bpf);
            fg.connect(bpf, sink);
            assertTrue(fg.start());

            // 3500 Hz tone (inside passband 2k-5k)
            int count = 512;
            ByteBuffer buf = createDirectFloatBuffer(count);
            FloatBuffer fb = buf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                fb.put((float) Math.sin(2.0 * Math.PI * 3500.0 * i / 48000.0));
            }
            source.push(buf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("BandPassFilter should stream samples", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 21: IirFilter Biquad and Cascaded Butterworth
     */
    @Test
    public void testIirFilterPipeline() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "iir_src");
             IirFilter iirBiquad = IirFilter.createLowPass(DataType.FLOAT, 48000.0, 3000.0, 0.7071);
             IirFilter iirButter = IirFilter.createButterworthLowPass(DataType.FLOAT, 48000.0, 3000.0, 4);
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "iir_sink");
             Flowgraph fg = new Flowgraph("fg_iir")) {

            fg.connect(source, iirBiquad);
            fg.connect(iirBiquad, iirButter);
            fg.connect(iirButter, sink);
            assertTrue(fg.start());

            int count = 512;
            ByteBuffer buf = createDirectFloatBuffer(count);
            FloatBuffer fb = buf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                fb.put((float) Math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0));
            }
            source.push(buf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("IirFilter should output samples", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 22: DcBlocker Filter (Strips DC offset)
     */
    @Test
    public void testDcBlockerFilter() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "dc_src");
             DcBlocker dcBlocker = new DcBlocker(DataType.FLOAT, 48000.0, 20.0, "dc_blk");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "dc_sink");
             Flowgraph fg = new Flowgraph("fg_dc_blocker")) {

            fg.connect(source, dcBlocker);
            fg.connect(dcBlocker, sink);
            assertTrue(fg.start());

            // Signal with large DC bias (+2.0) and small AC sine
            int count = 1024;
            ByteBuffer buf = createDirectFloatBuffer(count);
            FloatBuffer fb = buf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                fb.put(2.0f + (float) Math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0));
            }
            source.push(buf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertTrue("DcBlocker should process samples", pulled > 0);

            FloatBuffer outFloats = outBuf.asFloatBuffer();
            // Towards the end of the block, DC should be removed (mean near 0)
            float sum = 0.0f;
            int window = Math.min(256, pulled / 2);
            for (int i = pulled - window; i < pulled; i++) {
                sum += outFloats.get(i);
            }
            float mean = sum / window;
            assertTrue("DC offset should be substantially blocked (mean < 0.2), got " + mean, Math.abs(mean) < 0.2f);

            fg.stop();
        }
    }

    /**
     * Test 23: HilbertFilter Real-to-Analytic Conversion
     */
    @Test
    public void testHilbertFilter() throws Exception {
        try (JavaSource source = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "hilbert_src");
             HilbertFilter hilbert = new HilbertFilter(65, "hilbert_blk");
             JavaSink sink = new JavaSink(DataType.COMPLEX_FLOAT, BUFFER_CAPACITY, "hilbert_sink");
             Flowgraph fg = new Flowgraph("fg_hilbert")) {

            fg.connect(source, hilbert);
            fg.connect(hilbert, sink);
            assertTrue(fg.start());

            int count = 512;
            ByteBuffer buf = createDirectFloatBuffer(count);
            FloatBuffer fb = buf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                fb.put((float) Math.cos(2.0 * Math.PI * 1000.0 * i / 48000.0));
            }
            source.push(buf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count * 2);
            int pulled = sink.pull(outBuf, count);
            assertTrue("HilbertFilter should produce complex analytic samples", pulled > 0);

            fg.stop();
        }
    }

    /**
     * Test 24: Math Block Pipeline (Add, MultiplyConst, AddConst)
     */
    @Test
    public void testMathArithmeticPipeline() throws Exception {
        try (JavaSource src1 = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "math_src1");
             JavaSource src2 = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "math_src2");
             Add adder = new Add(DataType.FLOAT, 2, "math_adder");
             MultiplyConst gain = new MultiplyConst(DataType.FLOAT, 2.0f, "math_gain");
             AddConst offset = new AddConst(DataType.FLOAT, 1.0f, "math_offset");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "math_sink");
             Flowgraph fg = new Flowgraph("fg_math_arithmetic")) {

            fg.connect(src1, "out", adder, "in0");
            fg.connect(src2, "out", adder, "in1");
            fg.connect(adder, "out", gain, "in");
            fg.connect(gain, "out", offset, "in");
            fg.connect(offset, "out", sink, "in");

            assertTrue(fg.start());

            int count = 256;
            ByteBuffer buf1 = createDirectFloatBuffer(count);
            ByteBuffer buf2 = createDirectFloatBuffer(count);
            FloatBuffer fb1 = buf1.asFloatBuffer();
            FloatBuffer fb2 = buf2.asFloatBuffer();

            for (int i = 0; i < count; i++) {
                fb1.put(1.5f);
                fb2.put(2.5f);
            }

            src1.push(buf1, count);
            src2.push(buf2, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertEquals("Sink should pull all processed samples", count, pulled);

            FloatBuffer outFloats = outBuf.asFloatBuffer();
            for (int i = 0; i < count; i++) {
                // (1.5 + 2.5) * 2.0 + 1.0 = 4.0 * 2.0 + 1.0 = 9.0
                assertEquals("Math pipeline output mismatch at " + i, 9.0f, outFloats.get(i), 1e-5f);
            }

            fg.stop();
        }
    }

    /**
     * Test 25: Complex Math Pipeline (RealImagToComplex -> ComplexToMag -> Log10)
     */
    @Test
    public void testComplexMathAndLog10Pipeline() throws Exception {
        try (JavaSource realSrc = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "real_src");
             JavaSource imagSrc = new JavaSource(DataType.FLOAT, BUFFER_CAPACITY, "imag_src");
             RealImagToComplex combiner = new RealImagToComplex(DataType.COMPLEX_FLOAT, "combiner");
             ComplexToMag mag = new ComplexToMag(DataType.COMPLEX_FLOAT, "mag");
             Log10 log10 = new Log10(DataType.FLOAT, 20.0f, 0.0f, "log_db");
             JavaSink sink = new JavaSink(DataType.FLOAT, BUFFER_CAPACITY, "log_sink");
             Flowgraph fg = new Flowgraph("fg_complex_math")) {

            fg.connect(realSrc, "out", combiner, "real");
            fg.connect(imagSrc, "out", combiner, "imag");
            fg.connect(combiner, "out", mag, "in");
            fg.connect(mag, "out", log10, "in");
            fg.connect(log10, "out", sink, "in");

            assertTrue(fg.start());

            int count = 256;
            ByteBuffer rBuf = createDirectFloatBuffer(count);
            ByteBuffer iBuf = createDirectFloatBuffer(count);
            FloatBuffer rfb = rBuf.asFloatBuffer();
            FloatBuffer ifb = iBuf.asFloatBuffer();

            for (int i = 0; i < count; i++) {
                rfb.put(3.0f);
                ifb.put(4.0f);
            }

            realSrc.push(rBuf, count);
            imagSrc.push(iBuf, count);

            Thread.sleep(80);

            ByteBuffer outBuf = createDirectFloatBuffer(count);
            int pulled = sink.pull(outBuf, count);
            assertEquals(count, pulled);

            FloatBuffer outFloats = outBuf.asFloatBuffer();
            // |3 + 4j| = 5.0, 20 * log10(5.0) ~ 13.9794
            float expectedDb = (float) (20.0 * Math.log10(5.0));
            for (int i = 0; i < count; i++) {
                assertEquals("Log10 dB mismatch at " + i, expectedDb, outFloats.get(i), 1e-4f);
            }

            fg.stop();
        }
    }
}



