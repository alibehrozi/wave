package com.github.alibehrozi.wave.walkie;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.alibehrozi.wave.WaveApplication;
import com.github.alibehrozi.wave.microdsp.blocks.demodulation.FmDemodulator;
import com.github.alibehrozi.wave.microdsp.blocks.filters.FilterDesign;
import com.github.alibehrozi.wave.microdsp.blocks.modulation.FmModulator;
import com.github.alibehrozi.wave.microdsp.blocks.sinks.AudioSink;
import com.github.alibehrozi.wave.microdsp.blocks.sinks.SdrSink;
import com.github.alibehrozi.wave.microdsp.blocks.sources.JavaSource;
import com.github.alibehrozi.wave.microdsp.blocks.sources.SdrSource;
import com.github.alibehrozi.wave.microdsp.blocks.sources.SignalSource;
import com.github.alibehrozi.wave.microdsp.blocks.utils.RationalResampler;
import com.github.alibehrozi.wave.microdsp.blocks.utils.SquelchBlock;
import com.github.alibehrozi.wave.microdsp.core.DataType;
import com.github.alibehrozi.wave.microdsp.core.Flowgraph;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDevice;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.SdrManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the half-duplex DSP flowgraphs for the Walkie Talkie:
 * - RX Pipeline: SdrSource (2 MSPS) -> RationalResampler (decimation 3/125 to 48kHz) -> FmDemodulator -> SquelchBlock -> AudioSink (48kHz speaker)
 * - TX Pipeline: Java AudioRecord -> JavaSource (48kHz direct PCM) -> FmModulator -> RationalResampler (interpolation 125/3 to 2MSPS) -> SdrSink (2 MSPS HackRF)
 * - Dynamic hotplug support: attaches live SDR whenever connected, seamless fallback when disconnected.
 */
public class WalkieTalkieFlowgraphManager {

    private static final String TAG = "WalkieFlowgraphMgr";
    private static final int AUDIO_SAMPLE_RATE = 48000;
    private static final int SDR_SAMPLE_RATE = 2000000; // 2 MSPS

    public interface AudioLevelListener {
        void onAudioLevel(float rmsLevel, float[] waveform, boolean isTx);
    }

    public enum FmMode {
        NFM("NFM (Walkie-Talkie)", 5000.0, 3500.0, 0.01570796f),
        WFM("WFM (Broadcast FM)", 75000.0, 15000.0, 0.23561945f);

        public final String displayName;
        public final double peakDeviationHz;
        public final double audioCutoffHz;
        public final float txSensitivity; // 2 * pi * dev / 2MSPS

        FmMode(String displayName, double peakDeviationHz, double audioCutoffHz, float txSensitivity) {
            this.displayName = displayName;
            this.peakDeviationHz = peakDeviationHz;
            this.audioCutoffHz = audioCutoffHz;
            this.txSensitivity = txSensitivity;
        }
    }

    private final Context context;
    private AudioLevelListener audioLevelListener;

    private SdrDevice activeSdrDevice;
    private Flowgraph rxFlowgraph;
    private Flowgraph txFlowgraph;
    private AudioSink activeAudioSink;
    private JavaSource txAudioSource;

    private AudioRecord audioRecord;
    private Thread micCaptureThread;
    private final AtomicBoolean isTransmitting = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private double currentFrequencyHz = 446.00625e6; // Default PMR 1
    private float squelchThresholdDb = -45.0f;
    private float micGainMultiplier = 2.2f;
    private FmMode fmMode = FmMode.NFM;
    private final AtomicBoolean isTestToneMode = new AtomicBoolean(false);
    private final VoiceChanger voiceChanger = new VoiceChanger();
    private boolean isMuted = false;

    public WalkieTalkieFlowgraphManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        detectSdrHardware();
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
        if (activeAudioSink != null) {
            try {
                activeAudioSink.setVolume(muted ? 0.0f : 1.0f);
            } catch (Exception ignored) {}
        }
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void setVoiceEffect(@NonNull VoiceChanger.EffectType effect) {
        voiceChanger.setEffect(effect);
    }

    public VoiceChanger.EffectType getVoiceEffect() {
        return voiceChanger.getEffect();
    }

    public void setAudioLevelListener(@Nullable AudioLevelListener listener) {
        this.audioLevelListener = listener;
    }

    public synchronized void onSdrConnected(@NonNull SdrDevice device) {
        Log.i(TAG, "SDR hardware connected event: " + device.getDeviceInfo().getProduct());
        this.activeSdrDevice = device;
        if (isRunning.get() && !isTransmitting.get()) {
            startRxPipeline();
        }
    }

    public synchronized void onSdrDisconnected() {
        Log.i(TAG, "SDR hardware disconnected");
        this.activeSdrDevice = null;
        stopRxPipeline();
    }

    private void detectSdrHardware() {
        try {
            SdrManager sdrManager = WaveApplication.getSdrManager();
            List<SdrDevice> devices = sdrManager != null ? sdrManager.getConnectedDevices() : null;
            if (devices != null && !devices.isEmpty()) {
                activeSdrDevice = devices.get(0);
                Log.i(TAG, "SDR hardware detected: " + activeSdrDevice.getDeviceInfo().getProduct());
            } else {
                Log.i(TAG, "No SDR connected yet, operating in standalone Walkie Talkie mode");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query SDR devices during init", e);
        }
    }

    public boolean isSdrConnected() {
        return activeSdrDevice != null && activeSdrDevice.isConnected();
    }

    public boolean isHardwareConnected() {
        return isSdrConnected();
    }

    public String getDeviceName() {
        if (activeSdrDevice != null && activeSdrDevice.getDeviceInfo() != null) {
            return activeSdrDevice.getDeviceInfo().getProduct();
        }
        return "Internal Simulation";
    }

    public void setFrequency(double freqHz) {
        this.currentFrequencyHz = freqHz;
        // Auto-select WFM for commercial FM broadcast band (87.5 - 108.0 MHz)
        if (freqHz >= 87.0e6 && freqHz <= 108.5e6) {
            this.fmMode = FmMode.WFM;
        }
        if (activeSdrDevice != null) {
            try {
                activeSdrDevice.setFrequency((long) freqHz);
                Log.i(TAG, "SDR frequency updated to: " + freqHz + " Hz");
            } catch (Exception e) {
                Log.e(TAG, "Failed to set frequency on SDR", e);
            }
        }
    }

    public void setFmMode(@NonNull FmMode mode) {
        if (this.fmMode != mode) {
            this.fmMode = mode;
            Log.i(TAG, "FM mode changed to: " + mode.displayName);
            if (isRunning.get() && !isTransmitting.get()) {
                startRxPipeline();
            }
        }
    }

    public FmMode getFmMode() {
        return fmMode;
    }

    public void setTestToneMode(boolean enabled) {
        this.isTestToneMode.set(enabled);
        Log.i(TAG, "Test Tone mode set to: " + enabled);
    }

    public boolean isTestToneMode() {
        return isTestToneMode.get();
    }

    public void setSquelch(float thresholdDb) {
        this.squelchThresholdDb = thresholdDb;
    }

    public void setMicGain(float gain) {
        this.micGainMultiplier = Math.max(0.5f, Math.min(gain, 5.0f));
    }

    public synchronized void start() {
        isRunning.set(true);
        startRxPipeline();
    }

    public synchronized void stop() {
        isRunning.set(false);
        stopTx();
        stopRxPipeline();
    }

    private synchronized void startRxPipeline() {
        stopRxPipeline();

        if (activeSdrDevice != null && activeSdrDevice.supportsRx()) {
            try {
                activeSdrDevice.setFrequency((long) currentFrequencyHz);
                activeSdrDevice.setSampleRate(SDR_SAMPLE_RATE);

                rxFlowgraph = new Flowgraph("walkie_rx");

                // SDR 2 MSPS Source
                SdrSource sdrSource = new SdrSource(activeSdrDevice);

                // OpenSL ES Audio Sink for speaker output (48 kHz mono)
                activeAudioSink = new AudioSink(DataType.FLOAT, AUDIO_SAMPLE_RATE, 1, "audio_sink");
                activeAudioSink.initializeAudio();
                activeAudioSink.setVolume(isMuted ? 0.0f : 1.0f);
                activeAudioSink.startPlayback();

                if (fmMode == FmMode.WFM) {
                    // WFM RX: Demodulate wideband 2 MSPS complex -> 2 MSPS float audio, then decimate to 48 kHz
                    FmDemodulator fmDemod = new FmDemodulator(2.0f, "fm_demod");
                    float[] audioTaps = FilterDesign.lowPass(SDR_SAMPLE_RATE, 15000.0, 20000.0);
                    RationalResampler audioDecimator = new RationalResampler(DataType.FLOAT, 3, 125, audioTaps,
                            "audio_decimator");
                    SquelchBlock squelch = new SquelchBlock(DataType.FLOAT, squelchThresholdDb, "squelch");

                    rxFlowgraph.addBlock(sdrSource);
                    rxFlowgraph.addBlock(fmDemod);
                    rxFlowgraph.addBlock(audioDecimator);
                    rxFlowgraph.addBlock(squelch);
                    rxFlowgraph.addBlock(activeAudioSink);

                    rxFlowgraph.connect(sdrSource, fmDemod);
                    rxFlowgraph.connect(fmDemod, audioDecimator);
                    rxFlowgraph.connect(audioDecimator, squelch);
                    rxFlowgraph.connect(squelch, activeAudioSink);
                } else {
                    // NFM RX: Filter narrow channel (10 kHz) & decimate 2 MSPS -> 48 kHz complex, then demodulate
                    float[] rxTaps = FilterDesign.lowPass(SDR_SAMPLE_RATE, 10000.0, 15000.0);
                    RationalResampler rxResampler = new RationalResampler(DataType.COMPLEX_FLOAT, 3, 125, rxTaps,
                            "rx_resampler");
                    FmDemodulator fmDemod = new FmDemodulator(1.0f, "fm_demod");
                    SquelchBlock squelch = new SquelchBlock(DataType.FLOAT, squelchThresholdDb, "squelch");

                    rxFlowgraph.addBlock(sdrSource);
                    rxFlowgraph.addBlock(rxResampler);
                    rxFlowgraph.addBlock(fmDemod);
                    rxFlowgraph.addBlock(squelch);
                    rxFlowgraph.addBlock(activeAudioSink);

                    rxFlowgraph.connect(sdrSource, rxResampler);
                    rxFlowgraph.connect(rxResampler, fmDemod);
                    rxFlowgraph.connect(fmDemod, squelch);
                    rxFlowgraph.connect(squelch, activeAudioSink);
                }

                rxFlowgraph.start();
                Log.i(TAG, "Started SDR RX Flowgraph (" + fmMode.name() + ") at " + currentFrequencyHz + " Hz");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start SDR RX flowgraph", e);
            }
        }
    }

    private synchronized void stopRxPipeline() {
        if (rxFlowgraph != null) {
            try {
                rxFlowgraph.stop();
                rxFlowgraph.close();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping RX flowgraph", e);
            }
            rxFlowgraph = null;
        }

        if (activeAudioSink != null) {
            try {
                activeAudioSink.stopPlayback();
                activeAudioSink.cleanupAudio();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up AudioSink", e);
            }
            activeAudioSink = null;
        }
    }

    /**
     * Activates PTT (Push-To-Talk) and begins transmitting voice from microphone.
     */
    @SuppressLint("MissingPermission")
    public synchronized void startTx() {
        if (isTransmitting.getAndSet(true))
            return;

        // Stop RX first (half-duplex)
        stopRxPipeline();

        // If SDR supports transmission, setup TX flowgraph
        if (activeSdrDevice != null && activeSdrDevice.supportsTx()) {
            try {
                activeSdrDevice.setFrequency((long) currentFrequencyHz);
                activeSdrDevice.setSampleRate(SDR_SAMPLE_RATE); // 2 MSPS

                txFlowgraph = new Flowgraph("walkie_tx");

                if (isTestToneMode.get()) {
                    // Direct 1 kHz test tone generated natively at SDR rate
                    SignalSource toneSource = new SignalSource(DataType.FLOAT, SDR_SAMPLE_RATE, 1000.0, 1.0,
                            SignalSource.SignalType.SINE, "tone_src");
                    FmModulator fmModulator = new FmModulator(fmMode.txSensitivity, "fm_mod");
                    SdrSink sdrSink = new SdrSink(activeSdrDevice);

                    txFlowgraph.addBlock(toneSource);
                    txFlowgraph.addBlock(fmModulator);
                    txFlowgraph.addBlock(sdrSink);

                    txFlowgraph.connect(toneSource, fmModulator);
                    txFlowgraph.connect(fmModulator, sdrSink);

                    txFlowgraph.start();
                    Log.i(TAG, "Started SDR TX Test Tone Flowgraph (" + fmMode.name() + ", 1 kHz Sine @ 2 MSPS) at "
                            + currentFrequencyHz + " Hz");
                } else {
                    // Direct JavaSource pushes microphone PCM frames (48 kHz Float)
                    txAudioSource = new JavaSource(DataType.FLOAT, 65536, "tx_audio_source");

                    // Upsample in the audio domain from 48 kHz to 2 MSPS before FM modulation:
                    // Intermediate polyphase rate: 48,000 * 125 = 6,000,000 Hz
                    float[] audioTaps = FilterDesign.lowPass(6000000.0, fmMode.audioCutoffHz, 20000.0);
                    RationalResampler audioResampler = new RationalResampler(DataType.FLOAT, 125, 3, audioTaps,
                            "audio_resampler");

                    // FM Modulator running directly at 2 MSPS yields full unclipped FM Bessel sidebands
                    FmModulator fmModulator = new FmModulator(fmMode.txSensitivity, "fm_mod");

                    // SDR Sink transmits RF over HackRF hardware directly from FM Modulator
                    SdrSink sdrSink = new SdrSink(activeSdrDevice);

                    txFlowgraph.addBlock(txAudioSource);
                    txFlowgraph.addBlock(audioResampler);
                    txFlowgraph.addBlock(fmModulator);
                    txFlowgraph.addBlock(sdrSink);

                    txFlowgraph.connect(txAudioSource, audioResampler);
                    txFlowgraph.connect(audioResampler, fmModulator);
                    txFlowgraph.connect(fmModulator, sdrSink);

                    txFlowgraph.start();
                    Log.i(TAG, "Started SDR TX Flowgraph (" + fmMode.name() + ", sens=" + fmMode.txSensitivity + ") at "
                            + currentFrequencyHz + " Hz");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start SDR TX flowgraph", e);
            }
        }

        if (isTestToneMode.get()) {
            startTestToneVisualizerThread();
        } else {
            // Launch microphone capture loop which feeds the transmitter and visualizer
            startMicCaptureThread();
        }
    }

    /**
     * Releases PTT, emits classic roger beep, and returns to RX monitoring mode.
     */
    public synchronized void stopTx() {
        if (!isTransmitting.getAndSet(false))
            return;

        stopMicCaptureThread();

        if (txFlowgraph != null) {
            try {
                txFlowgraph.stop();
                txFlowgraph.close();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping TX flowgraph", e);
            }
            txFlowgraph = null;
        }
        txAudioSource = null;

        // Play classic NASA / Motorola dual-tone Roger Beep
        RogerBeepGenerator.playRogerBeep();

        // Resume RX mode if still active
        if (isRunning.get()) {
            startRxPipeline();
        }
    }

    public void setTestToneEnabled(boolean enabled) {
        this.isTestToneMode.set(enabled);
    }

    public boolean isTestToneEnabled() {
        return this.isTestToneMode.get();
    }

    public boolean isTransmitting() {
        return isTransmitting.get();
    }

    private void startTestToneVisualizerThread() {
        stopMicCaptureThread();

        micCaptureThread = new Thread(() -> {
            float[] normWaveform = new float[128];
            float phase = 0.0f;
            while (isTransmitting.get()) {
                // Generate 1 kHz synthetic wave for the oscilloscope
                for (int i = 0; i < normWaveform.length; i++) {
                    normWaveform[i] = (float) Math.sin(phase + (2.0 * Math.PI * i / 32.0));
                }
                phase += 0.4f;
                if (audioLevelListener != null) {
                    audioLevelListener.onAudioLevel(0.95f, normWaveform, true);
                }
                try {
                    Thread.sleep(33); // ~30 FPS
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "WalkieToneVisThread");

        micCaptureThread.start();
    }

    @SuppressLint("MissingPermission")
    private void startMicCaptureThread() {
        stopMicCaptureThread();

        micCaptureThread = new Thread(() -> {
            int minBufferSize = AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(minBufferSize, 2048);

            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        AUDIO_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize, running synthetic tone fallback");
                    runSyntheticAudioLoop();
                    return;
                }

                audioRecord.startRecording();
                short[] pcmBuffer = new short[512];
                float[] normWaveform = new float[128];

                // Direct ByteBuffer for feeding JavaSource with zero copy
                ByteBuffer directAudioBuffer = ByteBuffer.allocateDirect(512 * 4).order(ByteOrder.nativeOrder());
                FloatBuffer floatAudioBuffer = directAudioBuffer.asFloatBuffer();

                float[] floatSamples = new float[512];

                while (isTransmitting.get() && audioRecord != null) {
                    int read = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                    if (read > 0) {
                        // 1. Convert to float and apply mic gain
                        for (int i = 0; i < read; i++) {
                            floatSamples[i] = (pcmBuffer[i] / 32768.0f) * micGainMultiplier;
                        }

                        // 2. Real-time voice changer DSP effect
                        voiceChanger.process(floatSamples, read);

                        // 3. Feed directly into TX DSP pipeline with smooth analog limiter
                        floatAudioBuffer.clear();
                        long sumSq = 0;
                        for (int i = 0; i < read; i++) {
                            float sample = (float) Math.tanh(floatSamples[i]);
                            floatAudioBuffer.put(sample);

                            short s = (short) (sample * 32767.0f);
                            sumSq += (long) s * s;
                        }
                        directAudioBuffer.position(0);
                        directAudioBuffer.limit(read * 4);

                        JavaSource source = txAudioSource;
                        if (source != null && !source.isClosed()) {
                            source.push(directAudioBuffer, read);
                        }

                        // 2. Compute RMS for VU meter
                        double rms = Math.sqrt((double) sumSq / read);
                        float normalizedRms = (float) Math.min(1.0, rms / 15000.0);

                        // 3. Downsample for oscilloscope visualizer
                        int step = Math.max(1, read / normWaveform.length);
                        for (int j = 0; j < normWaveform.length; j++) {
                            int idx = Math.min(j * step, read - 1);
                            normWaveform[j] = (pcmBuffer[idx] / 32768.0f) * micGainMultiplier;
                        }

                        // 4. Dispatch to visualizer listener
                        if (audioLevelListener != null) {
                            audioLevelListener.onAudioLevel(normalizedRms, normWaveform, true);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in mic capture thread", e);
            } finally {
                if (audioRecord != null) {
                    try {
                        audioRecord.stop();
                        audioRecord.release();
                    } catch (Exception ignored) {
                    }
                    audioRecord = null;
                }
            }
        }, "WalkieMicThread");

        micCaptureThread.start();
    }

    private void runSyntheticAudioLoop() {
        int samplesPerChunk = 512;
        ByteBuffer directAudioBuffer = ByteBuffer.allocateDirect(samplesPerChunk * 4).order(ByteOrder.nativeOrder());
        FloatBuffer floatAudioBuffer = directAudioBuffer.asFloatBuffer();
        float[] normWaveform = new float[128];

        double phase = 0.0;
        double phaseInc = 2.0 * Math.PI * 1000.0 / AUDIO_SAMPLE_RATE;

        while (isTransmitting.get()) {
            floatAudioBuffer.clear();
            for (int i = 0; i < samplesPerChunk; i++) {
                float sample = (float) Math.sin(phase);
                floatAudioBuffer.put(sample);
                phase += phaseInc;
                if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI;
            }
            directAudioBuffer.position(0);
            directAudioBuffer.limit(samplesPerChunk * 4);

            JavaSource source = txAudioSource;
            if (source != null && !source.isClosed()) {
                source.push(directAudioBuffer, samplesPerChunk);
            }

            for (int j = 0; j < normWaveform.length; j++) {
                normWaveform[j] = (float) Math.sin(phase + (2.0 * Math.PI * j / 32.0));
            }
            if (audioLevelListener != null) {
                audioLevelListener.onAudioLevel(0.9f, normWaveform, true);
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void stopMicCaptureThread() {
        if (micCaptureThread != null) {
            micCaptureThread.interrupt();
            micCaptureThread = null;
        }
    }
}
