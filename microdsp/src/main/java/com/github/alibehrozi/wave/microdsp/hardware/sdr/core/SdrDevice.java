package com.github.alibehrozi.wave.microdsp.hardware.sdr.core;

import androidx.annotation.NonNull;

/**
 * Represents a connected Software Defined Radio (SDR) device.
 *
 * <p>This interface provides a common abstraction for controlling SDR hardware,
 * querying front-end capabilities (e.g. duplex modes, RX/TX support), and managing
 * IQ data streaming. Implementations may represent different physical SDR devices
 * such as HackRF, RTL-SDR, or ADALM-Pluto.</p>
 *
 * <p>Device discovery, USB permissions, USB connection management, and
 * device-specific driver creation are handled outside this interface,
 * typically by {@code SdrManager} and the USB layer.</p>
 *
 * <p>The SDR hardware abstraction is independent of the DSP sample format.
 * The DSP layer uses a fixed {@code complex<float>} representation, while
 * each SDR implementation is responsible for converting between its native
 * hardware sample format and the DSP format.</p>
 */
public interface SdrDevice extends AutoCloseable {

    /**
     * Checks whether the SDR is currently connected and usable.
     * @return {@code true} if the device is connected and usable,
     * {@code false} otherwise
     */
    boolean isConnected();

    /**
     * Closes the SDR and releases all associated resources.
     *
     * <p>Any active RX or TX streams should be stopped before resources
     * are released. After this method returns, {@link #isConnected()}
     * should return {@code false}.</p>
     *
     * <p>This method should be safe to call multiple times.</p>
     */
    @Override
    void close();

    /**
     * Returns static identification and hardware information about the SDR.
     * @return device information
     */
    @NonNull
    SdrDeviceInfo getDeviceInfo();

    /**
     * Performs a hardware reset.
     *
     * <p>The exact behavior of a reset is device-specific. Implementations
     * should document whether active RX or TX streams are stopped as part
     * of the reset operation.</p>
     * @return {@code 0} on success, negative error code on failure
     */
    int reset();


    /**
     * Sets the center frequency.
     * @param frequencyHz center frequency in Hertz
     * @return {@code 0} on success, negative error code on failure
     */
    int setFrequency(long frequencyHz);

    /**
     * Gets the currently configured center frequency.
     * @return center frequency in Hertz
     */
    long getFrequency();

    /**
     * Sets the sample rate.
     * @param rateHz sample rate in Hertz
     * @return {@code 0} on success, negative error code on failure
     */
    int setSampleRate(long rateHz);

    /**
     * Gets the currently configured sample rate.
     * @return sample rate in Hertz
     */
    long getSampleRate();

    /**
     * Sets the SDR gain.
     *
     * <p>The exact meaning of gain may be device-specific. An SDR may
     * internally use multiple gain stages while exposing a single logical
     * gain value through this interface.</p>
     * @param gainDb gain in decibels
     * @return {@code 0} on success, negative error code on failure
     */
    int setGain(int gainDb);

    /**
     * Gets the currently configured SDR gain.
     * @return gain in decibels
     */
    int getGain();

    /**
     * Starts receiving IQ samples from the SDR.
     * @return 0 on success, negative error code on failure
     */
    int startRx();

    /**
     * Stops receiving IQ samples from the SDR.
     * @return {@code 0} on success, negative error code on failure
     */
    int stopRx();

    /**
     * Starts transmitting IQ samples to the SDR.
     * @return 0 on success, negative error code on failure
     */
    int startTx();

    /**
     * Stops transmitting IQ samples to the SDR.
     * @return {@code 0} on success, negative error code on failure
     */
    int stopTx();

    /**
     * Returns the current RX/TX streaming state.
     *
     * <p>The state represents whether the device is idle, receiving,
     * transmitting, or both receiving and transmitting simultaneously.</p>
     * @return current streaming state
     */
    @NonNull
    SdrStreamingState getStreamingState();

    /**
     * Returns the native handle associated with this SDR.
     *
     * <p>The handle is used by the native C++ DSP layer to access the
     * underlying SDR implementation. The returned value is opaque to
     * Java code and must not be modified or released directly by callers.</p>
     * @return native handle, or {@code 0} if no valid native handle exists
     */
    long getNativeHandle();

    // --- Capability Helpers ---

    /**
     * Returns the logical role and duplex capability of this SDR device.
     * @return SDR role
     */
    @NonNull
    default SdrRole getRole() {
        SdrDeviceInfo info = getDeviceInfo();
        return info != null ? info.getRole() : SdrRole.UNASSIGNED;
    }

    /**
     * Checks whether this SDR supports receiving RF signals.
     * @return {@code true} if receiving is supported
     */
    default boolean supportsRx() {
        return getRole().supportsRx();
    }

    /**
     * Checks whether this SDR supports transmitting RF signals.
     * @return {@code true} if transmitting is supported
     */
    default boolean supportsTx() {
        return getRole().supportsTx();
    }

    /**
     * Checks whether this SDR supports simultaneous transmission and reception (full-duplex).
     * @return {@code true} if full-duplex operation is supported
     */
    default boolean isFullDuplex() {
        return getRole().isFullDuplex();
    }

    /**
     * Checks whether this SDR operates in half-duplex (RX or TX, but not simultaneously).
     * @return {@code true} if half-duplex operation is supported
     */
    default boolean isHalfDuplex() {
        return getRole().isHalfDuplex();
    }
}