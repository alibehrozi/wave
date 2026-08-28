package com.github.alibehrozi.wave.microdsp.blocks.sinks;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;
import com.github.alibehrozi.wave.microdsp.core.Port;

/**
 * Audio output sink block for playing audio through the system's audio hardware.
 * Supports both FLOAT and SHORT data types for audio playback.
 *
 * <p>Example usage:
 * <pre>
 * AudioSink sink = new AudioSink(DataType.FLOAT, 44100, 2);
 * sink.setVolume(0.8f);
 * sink.initializeAudio();
 * sink.startPlayback();
 * </pre>
 */
public class AudioSink extends Block {

    /**
     * Audio configuration parameters.
     */
    public static class AudioConfig {
        public int sampleRate;
        public int channels;
        public int bufferSize;
        public int bitsPerSample;

        /**
         * Create audio configuration.
         * @param sampleRate sample rate in Hz
         * @param channels   number of audio channels
         */
        public AudioConfig(int sampleRate, int channels) {
            if (sampleRate <= 0 || sampleRate > 192000) {
                throw new IllegalArgumentException("Invalid sample rate: " + sampleRate);
            }
            if (channels <= 0 || channels > 8) {
                throw new IllegalArgumentException("Invalid channel count: " + channels);
            }
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bufferSize = 1024;
            this.bitsPerSample = 16;
        }

        /**
         * Create audio configuration with custom buffer size.
         * @param sampleRate sample rate in Hz
         * @param channels   number of audio channels
         * @param bufferSize buffer size in samples
         */
        public AudioConfig(int sampleRate, int channels, int bufferSize) {
            this(sampleRate, channels);
            if (bufferSize <= 0) {
                throw new IllegalArgumentException("Buffer size must be positive");
            }
            this.bufferSize = bufferSize;
        }
    }

    private int sampleRate;
    private int channels;
    private float volume;
    private int bufferSize;
    private boolean initialized;

    /**
     * Create a new AudioSink block.
     * @param dataType   data type (FLOAT or SHORT only)
     * @param sampleRate audio sample rate in Hz (must be between 1 and 192000)
     * @param channels   number of audio channels (1-8)
     * @param name       block name
     * @throws IllegalArgumentException if dataType is null or not FLOAT/SHORT,
     *                                  or if sampleRate/channels are invalid
     */
    public AudioSink(DataType dataType, int sampleRate, int channels, String name) {
        super(name, nativeCreateAudioSink(
                validateDataType(dataType).ordinal(),
                validateSampleRate(sampleRate),
                validateChannels(channels),
                validateName(name)));

        this.sampleRate = sampleRate;
        this.channels = channels;
        this.volume = 1.0f;
        this.bufferSize = 1024;
        this.initialized = false;
    }

    private static DataType validateDataType(DataType type) {
        if (type == null) {
            throw new IllegalArgumentException("DataType cannot be null");
        }
        if (type != DataType.FLOAT && type != DataType.SHORT) {
            throw new IllegalArgumentException("AudioSink only supports FLOAT and SHORT data types");
        }
        return type;
    }

    private static int validateSampleRate(int sampleRate) {
        if (sampleRate <= 0 || sampleRate > 192000) {
            throw new IllegalArgumentException("Invalid sample rate: " + sampleRate + " (must be 1-192000)");
        }
        return sampleRate;
    }

    private static int validateChannels(int channels) {
        if (channels <= 0 || channels > 8) {
            throw new IllegalArgumentException("Invalid channel count: " + channels + " (must be 1-8)");
        }
        return channels;
    }

    private static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        return name;
    }

    /**
     * Create a new AudioSink with default name.
     * @param dataType   data type (FLOAT or SHORT only)
     * @param sampleRate audio sample rate in Hz
     * @param channels   number of audio channels
     */
    public AudioSink(DataType dataType, int sampleRate, int channels) {
        this(dataType, sampleRate, channels, "audio_sink");
    }

    /**
     * Create a new AudioSink with default name and default sample rate (44100Hz).
     * @param dataType data type (FLOAT or SHORT only)
     * @param channels number of audio channels
     */
    public AudioSink(DataType dataType, int channels) {
        this(dataType, 44100, channels, "audio_sink");
    }

    /**
     * Create a new AudioSink with default name, default sample rate (44100Hz),
     * and mono (1 channel).
     * @param dataType data type (FLOAT or SHORT only)
     */
    public AudioSink(DataType dataType) {
        this(dataType, 44100, 1, "audio_sink");
    }

    /**
     * Initialize audio system.
     * @return true if initialization successful
     * @throws IllegalStateException if block is closed
     */
    public boolean initializeAudio() {
        checkNotClosed();
        boolean result = nativeInitializeAudio(nativeHandle);
        if (result) {
            initialized = true;
        }
        return result;
    }

    /**
     * Clean up audio resources.
     * @throws IllegalStateException if block is closed
     */
    public void cleanupAudio() {
        checkNotClosed();
        nativeCleanupAudio(nativeHandle);
        initialized = false;
    }

    /**
     * Start audio playback.
     * @return true if playback started successfully
     * @throws IllegalStateException if block is closed
     */
    public boolean startPlayback() {
        checkNotClosed();
        return nativeStartPlayback(nativeHandle);
    }

    /**
     * Stop audio playback.
     * @throws IllegalStateException if block is closed
     */
    public void stopPlayback() {
        checkNotClosed();
        nativeStopPlayback(nativeHandle);
    }

    /**
     * Pause audio playback.
     * @throws IllegalStateException if block is closed
     */
    public void pausePlayback() {
        checkNotClosed();
        nativePausePlayback(nativeHandle);
    }

    /**
     * Resume audio playback.
     * @throws IllegalStateException if block is closed
     */
    public void resumePlayback() {
        checkNotClosed();
        nativeResumePlayback(nativeHandle);
    }

    /**
     * Set audio sample rate.
     * Can only be set before initialization.
     * @param sampleRate sample rate in Hz (must be between 1 and 192000)
     * @return true if sample rate was set successfully
     * @throws IllegalArgumentException if sampleRate is invalid
     * @throws IllegalStateException    if block is closed or already initialized
     */
    public boolean setSampleRate(int sampleRate) {
        if (sampleRate <= 0 || sampleRate > 192000) {
            throw new IllegalArgumentException("Invalid sample rate: " + sampleRate + " (must be 1-192000)");
        }
        checkNotClosed();
        if (initialized) {
            throw new IllegalStateException("Cannot change sample rate after initialization");
        }
        boolean result = nativeSetSampleRate(nativeHandle, sampleRate);
        if (result) {
            this.sampleRate = sampleRate;
        }
        return result;
    }

    /**
     * Get current sample rate.
     * @return sample rate in Hz
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Set number of audio channels.
     * Can only be set before initialization.
     * @param channels number of channels (1=mono, 2=stereo, max 8)
     * @return true if channels were set successfully
     * @throws IllegalArgumentException if channels is invalid
     * @throws IllegalStateException    if block is closed or already initialized
     */
    public boolean setChannels(int channels) {
        if (channels <= 0 || channels > 8) {
            throw new IllegalArgumentException("Invalid channel count: " + channels + " (must be 1-8)");
        }
        checkNotClosed();
        if (initialized) {
            throw new IllegalStateException("Cannot change channels after initialization");
        }
        boolean result = nativeSetChannels(nativeHandle, channels);
        if (result) {
            this.channels = channels;
        }
        return result;
    }

    /**
     * Get number of audio channels.
     * @return number of channels
     */
    public int getChannels() {
        return channels;
    }

    /**
     * Set audio volume.
     * @param volume volume level (0.0 to 1.0)
     * @throws IllegalArgumentException if volume is outside valid range
     * @throws IllegalStateException    if block is closed
     */
    public void setVolume(float volume) {
        if (volume < 0.0f || volume > 1.0f) {
            throw new IllegalArgumentException("Volume must be between 0.0 and 1.0");
        }
        checkNotClosed();
        this.volume = volume;
        nativeSetVolume(nativeHandle, volume);
    }

    /**
     * Get current volume.
     * @return volume level (0.0 to 1.0)
     */
    public float getVolume() {
        return volume;
    }

    /**
     * Set audio buffer size.
     * Can only be set before initialization.
     * @param bufferSize buffer size in samples (must be positive)
     * @throws IllegalArgumentException if bufferSize is not positive
     * @throws IllegalStateException    if block is closed or already initialized
     */
    public void setBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        checkNotClosed();
        if (initialized) {
            throw new IllegalStateException("Cannot change buffer size after initialization");
        }
        this.bufferSize = bufferSize;
        nativeSetBufferSize(nativeHandle, bufferSize);
    }

    /**
     * Get current buffer size.
     * @return buffer size in samples
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Check if audio is currently playing.
     * @return true if playing
     * @throws IllegalStateException if block is closed
     */
    public boolean isPlaying() {
        checkNotClosed();
        return nativeIsPlaying(nativeHandle);
    }

    /**
     * Check if audio system is initialized.
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get audio latency in milliseconds.
     * @return latency in ms
     * @throws IllegalStateException if block is closed
     */
    public int getLatency() {
        checkNotClosed();
        return nativeGetLatency(nativeHandle);
    }

    /**
     * Get total samples played.
     * @return number of samples played
     * @throws IllegalStateException if block is closed
     */
    public long getSamplesPlayed() {
        checkNotClosed();
        return nativeGetSamplesPlayed(nativeHandle);
    }

    /**
     * Get the audio configuration.
     * @return AudioConfig object
     */
    public AudioConfig getConfig() {
        return new AudioConfig(sampleRate, channels, bufferSize);
    }

    /**
     * Create a Java Port wrapper for a native port.
     * @param portHandle native port pointer
     * @param direction  port direction
     * @param name       port name
     * @return Port wrapper
     */
    @Override
    protected Port createPortWrapper(long portHandle, Port.Direction direction, String name) {
        return new Port(portHandle, name, direction);
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("AudioSink is closed");
        }
    }

    public boolean isClosed() {
        return nativeHandle == 0;
    }

    // Native methods
    private static native long nativeCreateAudioSink(int dataType, int sampleRate, int channels, String name);
    private native void nativeDestroyAudioSink(long handle);
    private native boolean nativeInitializeAudio(long handle);
    private native void nativeCleanupAudio(long handle);
    private native boolean nativeStartPlayback(long handle);
    private native void nativeStopPlayback(long handle);
    private native void nativePausePlayback(long handle);
    private native void nativeResumePlayback(long handle);
    private native boolean nativeSetSampleRate(long handle, int sampleRate);
    private native boolean nativeSetChannels(long handle, int channels);
    private native void nativeSetVolume(long handle, float volume);
    private native void nativeSetBufferSize(long handle, int bufferSize);
    private native boolean nativeIsPlaying(long handle);
    private native boolean nativeIsInitialized(long handle);
    private native int nativeGetLatency(long handle);
    private native long nativeGetSamplesPlayed(long handle);
}