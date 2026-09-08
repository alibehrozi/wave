package com.github.alibehrozi.wave.walkie;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Synthesizes classic NASA / Motorola dual-tone Roger Beeps on PTT release.
 */
public final class RogerBeepGenerator {

    private static final String TAG = "RogerBeepGenerator";
    private static final int SAMPLE_RATE = 44100;

    private RogerBeepGenerator() {}

    /**
     * Plays a classic 2-tone roger beep (1200 Hz tone for 70ms, then 1500 Hz tone for 70ms).
     */
    public static void playRogerBeep() {
        new Thread(() -> {
            try {
                int tone1Samples = (int) (SAMPLE_RATE * 0.070); // 70ms
                int tone2Samples = (int) (SAMPLE_RATE * 0.070); // 70ms
                int totalSamples = tone1Samples + tone2Samples;

                short[] buffer = new short[totalSamples];

                double freq1 = 1200.0;
                double freq2 = 1500.0;

                // Tone 1
                for (int i = 0; i < tone1Samples; i++) {
                    double angle = 2.0 * Math.PI * i / (SAMPLE_RATE / freq1);
                    // Smooth envelope window (fade in/out) to prevent clicks
                    double env = Math.sin(Math.PI * i / tone1Samples);
                    buffer[i] = (short) (Math.sin(angle) * 26000.0 * env);
                }

                // Tone 2
                for (int i = 0; i < tone2Samples; i++) {
                    double angle = 2.0 * Math.PI * i / (SAMPLE_RATE / freq2);
                    double env = Math.sin(Math.PI * i / tone2Samples);
                    buffer[tone1Samples + i] = (short) (Math.sin(angle) * 28000.0 * env);
                }

                int bufferSize = totalSamples * 2;
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                track.write(buffer, 0, totalSamples);
                track.play();

                // Wait for playback and release
                Thread.sleep(170);
                track.stop();
                track.release();
            } catch (Exception e) {
                Log.e(TAG, "Error playing roger beep", e);
            }
        }, "RogerBeepThread").start();
    }
}
