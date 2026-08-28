package com.github.alibehrozi.wave.microdsp.blocks.sources;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * Audio input block that captures audio from the system's microphone.
 */
public class AudioSource extends Block {

    private final DataType dataType;
    private final int sampleRate;
    private final int channels;

    /**
     * Audio configuration parameters for AudioSource.
     */
    public static class AudioConfig {
        public int sampleRate;
        public int channels;
        public int bufferSize;

        /**
         * Create audio configuration for AudioSource.
         * @param sampleRate sample rate in Hz (1-192000)
         * @param channels   number of audio channels (1-8)
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

    /**
     * Create a new AudioSource.
     * @param dataType   Data type to output (FLOAT or SHORT)
     * @param sampleRate Audio sample rate in Hz (e.g., 44100, 48000)
     * @param channels   Number of audio channels (1 for mono, 2 for stereo)
     * @param name       Block name
     */
    public AudioSource(@NonNull DataType dataType, int sampleRate, int channels, @NonNull String name) {
        super(name, nativeCreateAudioSource(dataType.ordinal(), sampleRate, channels, name));
        this.dataType = dataType;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    /**
     * Create a new AudioSource with default name.
     * @param dataType   Data type to output
     * @param sampleRate Audio sample rate in Hz
     * @param channels   Number of audio channels
     */
    public AudioSource(@NonNull DataType dataType, int sampleRate, int channels) {
        this(dataType, sampleRate, channels, "audio_source");
    }

    /**
     * Checks if the required RECORD_AUDIO permission has been granted.
     * @param context Application or activity context
     * @return true if RECORD_AUDIO permission is granted
     */
    public static boolean hasPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Activate microphone capture.
     * @return true if microphone started successfully
     */
    public boolean activate() {
        return start();
    }

    /**
     * Deactivate microphone capture (stops microphone recording).
     */
    public void deactivate() {
        stop();
    }

    /**
     * Enable or disable microphone capture.
     * @param active true to activate microphone, false to deactivate
     * @return true if activated successfully
     */
    public boolean setActive(boolean active) {
        if (active) {
            return activate();
        } else {
            deactivate();
            return false;
        }
    }

    /**
     * Start audio recording from microphone.
     * @return true if recording started successfully
     */
    public boolean startRecording() {
        return start();
    }

    /**
     * Stop audio recording from microphone.
     */
    public void stopRecording() {
        stop();
    }

    /**
     * Check if microphone audio capture is currently active.
     * @return true if actively recording
     */
    public boolean isRecording() {
        return isStarted() && nativeIsRecording(getNativeHandle());
    }

    /**
     * Get the data type produced by this audio source.
     * @return Output data type
     */
    public DataType getDataType() {
        return dataType;
    }

    /**
     * Get the audio sample rate.
     * @return Sample rate in Hz
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Get the number of audio channels.
     * @return Channel count
     */
    public int getChannels() {
        return channels;
    }

    private static native long nativeCreateAudioSource(int dataType, int sampleRate, int channels, String name);
    private native boolean nativeIsRecording(long handle);
}
