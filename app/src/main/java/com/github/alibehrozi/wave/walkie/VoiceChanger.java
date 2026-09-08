package com.github.alibehrozi.wave.walkie;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time audio DSP voice changer effects processor for Walkie Talkie
 * transmission.
 */
public class VoiceChanger {

    public enum EffectType {
        NORMAL("Natural", "Clean Mic Direct", "DSP: BYPASS", R.drawable.ic_mic_24, 0xFF0969DA),
        ROBOTIC("Robotic", "Ring Modulator", "DSP: 85Hz SINE", R.drawable.ic_robot_24, 0xFF8250DF),
        RADIO_COMM("Mil Radio", "Bandpass & Grit", "DSP: 300-3.4kHz", R.drawable.ic_radio_24, 0xFF1A7F37),
        DEEP_VOICE("Deep Pitch", "Low Sub-harmonics", "DSP: PITCH DOWN", R.drawable.ic_deep_voice_24, 0xFFCF222E),
        CHIPMUNK("Helium", "High Pitch Formant", "DSP: PITCH UP", R.drawable.ic_helium_24, 0xFFBF3989),
        SCRAMBLER("Scrambler", "Spectral Invert", "DSP: INV 3.3kHz", R.drawable.ic_scrambler_24, 0xFFBC4C00);

        public final String title;
        public final String subtitle;
        public final String dspTag;
        @DrawableRes
        public final int iconRes;
        public final int colorAccent;

        EffectType(String title, String subtitle, String dspTag, @DrawableRes int iconRes, int colorAccent) {
            this.title = title;
            this.subtitle = subtitle;
            this.dspTag = dspTag;
            this.iconRes = iconRes;
            this.colorAccent = colorAccent;
        }
    }

    private EffectType currentEffect = EffectType.NORMAL;

    // DSP internal state variables
    private double robotPhase = 0.0;
    private double scramblerPhase = 0.0;

    // Filter states for Radio Comm (Bandpass IIR: 400Hz HPF + 3200Hz LPF)
    private float hpPrevX = 0.0f;
    private float hpPrevY = 0.0f;
    private float lpPrevY = 0.0f;

    // Pitch shifter granular delay buffer (48kHz sample rate)
    private static final int PITCH_BUF_SIZE = 2048;
    private final float[] pitchBuffer = new float[PITCH_BUF_SIZE];
    private int pitchWritePos = 0;
    private float pitchReadPosUp = 0.0f;
    private float pitchReadPosDown = 0.0f;

    public synchronized void setEffect(@NonNull EffectType effect) {
        this.currentEffect = effect;
        resetState();
    }

    public synchronized EffectType getEffect() {
        return currentEffect;
    }

    private void resetState() {
        robotPhase = 0.0;
        scramblerPhase = 0.0;
        hpPrevX = 0.0f;
        hpPrevY = 0.0f;
        lpPrevY = 0.0f;
        pitchWritePos = 0;
        pitchReadPosUp = 0.0f;
        pitchReadPosDown = 0.0f;
    }

    /**
     * In-place real-time DSP processing on float samples (typically range -1.0 to
     * 1.0 at 48 kHz).
     */
    public synchronized void process(float[] samples, int count) {
        if (currentEffect == EffectType.NORMAL || count <= 0) {
            return;
        }

        switch (currentEffect) {
            case ROBOTIC:
                processRobotic(samples, count);
                break;
            case RADIO_COMM:
                processRadioComm(samples, count);
                break;
            case DEEP_VOICE:
                processDeepVoice(samples, count);
                break;
            case CHIPMUNK:
                processChipmunk(samples, count);
                break;
            case SCRAMBLER:
                processScrambler(samples, count);
                break;
            default:
                break;
        }
    }

    /**
     * Ring modulation with 85 Hz carrier for metallic robotic speech
     */
    private void processRobotic(float[] samples, int count) {
        final double phaseInc = 2.0 * Math.PI * 85.0 / 48000.0;
        for (int i = 0; i < count; i++) {
            float carrier = (float) Math.sin(robotPhase);
            robotPhase += phaseInc;
            if (robotPhase > 2.0 * Math.PI)
                robotPhase -= 2.0 * Math.PI;

            // Blend 70% ring modulation with 30% direct voice
            samples[i] = (samples[i] * carrier * 1.3f) + (samples[i] * 0.3f);
        }
    }

    /**
     * Bandpass filter (400 Hz - 3200 Hz) + non-linear clipping for authentic
     * CB/military walkie sound
     */
    private void processRadioComm(float[] samples, int count) {
        // Simple 1-pole highpass (~400 Hz) & 1-pole lowpass (~3200 Hz) at 48 kHz
        final float hpAlpha = 0.95f;
        final float lpAlpha = 0.35f;

        for (int i = 0; i < count; i++) {
            float in = samples[i];

            // Highpass
            float hpOut = hpAlpha * (hpPrevY + in - hpPrevX);
            hpPrevX = in;
            hpPrevY = hpOut;

            // Lowpass
            float lpOut = lpPrevY + lpAlpha * (hpOut - lpPrevY);
            lpPrevY = lpOut;

            // Soft-clipping overdrive
            samples[i] = (float) Math.tanh(lpOut * 2.8f);
        }
    }

    /**
     * Deep voice pitch down using delay line crossfade
     */
    private void processDeepVoice(float[] samples, int count) {
        final float rate = 0.75f; // ~4 semitones down
        final float grainSize = 1024.0f;

        for (int i = 0; i < count; i++) {
            pitchBuffer[pitchWritePos] = samples[i];

            int idx1 = ((int) pitchReadPosDown) % PITCH_BUF_SIZE;
            int idx2 = ((int) (pitchReadPosDown + grainSize * 0.5f)) % PITCH_BUF_SIZE;

            float frac = (pitchReadPosDown % (grainSize * 0.5f)) / (grainSize * 0.5f);
            float outSample = pitchBuffer[idx1] * (1.0f - frac) + pitchBuffer[idx2] * frac;

            pitchWritePos = (pitchWritePos + 1) % PITCH_BUF_SIZE;
            pitchReadPosDown += rate;
            if (pitchReadPosDown >= PITCH_BUF_SIZE)
                pitchReadPosDown -= PITCH_BUF_SIZE;

            samples[i] = (outSample * 0.8f) + (samples[i] * 0.2f);
        }
    }

    /**
     * Helium / Chipmunk pitch up using delay line crossfade
     */
    private void processChipmunk(float[] samples, int count) {
        final float rate = 1.38f; // ~5.5 semitones up
        final float grainSize = 768.0f;

        for (int i = 0; i < count; i++) {
            pitchBuffer[pitchWritePos] = samples[i];

            int idx1 = ((int) pitchReadPosUp) % PITCH_BUF_SIZE;
            int idx2 = ((int) (pitchReadPosUp + grainSize * 0.5f)) % PITCH_BUF_SIZE;

            float frac = (pitchReadPosUp % (grainSize * 0.5f)) / (grainSize * 0.5f);
            float outSample = pitchBuffer[idx1] * (1.0f - frac) + pitchBuffer[idx2] * frac;

            pitchWritePos = (pitchWritePos + 1) % PITCH_BUF_SIZE;
            pitchReadPosUp += rate;
            if (pitchReadPosUp >= PITCH_BUF_SIZE)
                pitchReadPosUp -= PITCH_BUF_SIZE;

            samples[i] = outSample;
        }
    }

    /**
     * Spectral Inversion Scrambler (3300 Hz carrier inversion)
     */
    private void processScrambler(float[] samples, int count) {
        final double phaseInc = 2.0 * Math.PI * 3300.0 / 48000.0;
        for (int i = 0; i < count; i++) {
            float carrier = (float) Math.cos(scramblerPhase);
            scramblerPhase += phaseInc;
            if (scramblerPhase > 2.0 * Math.PI)
                scramblerPhase -= 2.0 * Math.PI;

            // Heterodyne frequency inversion
            samples[i] = samples[i] * carrier * 1.5f;
        }
    }

    public static List<EffectType> getAllEffects() {
        List<EffectType> list = new ArrayList<>();
        for (EffectType e : EffectType.values()) {
            list.add(e);
        }
        return list;
    }
}
