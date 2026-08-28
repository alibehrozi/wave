package com.github.alibehrozi.wave.microdsp.hardware.sdr.drivers.hackrf;

/**
 * Provides the native bridge used by {@link HackRfDevice}.
 *
 * <p>The Java SDR layer intentionally does not implement the HackRF USB
 * protocol itself. The native layer is responsible for talking to the
 * HackRF hardware and for streaming IQ samples.</p>
 *
 * <p>The native implementation is expected to be backed by the HackRF
 * native library or by the project's own native USB/HackRF implementation.
 * The native layer receives the Android USB connection file descriptor
 * that is owned by {@code UsbConnectionController}.</p>
 */
final class HackRfNative {

    /*
     * Load the native DSP library. This must happen before any JNI method
     * is called. If the library is missing, an UnsatisfiedLinkError is thrown
     * here (at class load time) rather than later at an unrelated call site.
     */
    static {
        System.loadLibrary("microdsp");
    }

    /**
     * Native streaming state: no active stream.
     */
    public static final int STREAMING_IDLE = 0;

    /**
     * Native streaming state: receiving.
     */
    public static final int STREAMING_RX = 1;

    /**
     * Native streaming state: transmitting.
     */
    public static final int STREAMING_TX = 2;

    /**
     * Creates a native HackRF handle from an already authorized
     * Android USB connection.
     *
     * <p>The native implementation must not close the Android USB
     * connection. Ownership remains with {@code UsbConnectionController}.</p>
     * @param usbFileDescriptor Android USB connection file descriptor
     * @return native HackRF handle, or {@code 0} on failure
     */
    public static native long create(int usbFileDescriptor);

    /**
     * Closes a native HackRF handle.
     * @param nativeHandle native HackRF handle
     */
    public static native void close(long nativeHandle);

    /**
     * Checks whether the native HackRF handle is still valid.
     * @param nativeHandle native HackRF handle
     * @return {@code true} if the handle is connected and usable
     */
    public static native boolean isConnected(long nativeHandle);

    /**
     * Performs a hardware reset.
     * @param nativeHandle native HackRF handle
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int reset(long nativeHandle);

    /**
     * Sets the center frequency.
     * @param nativeHandle native HackRF handle
     * @param frequencyHz  center frequency in Hertz
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int setFrequency(long nativeHandle, long frequencyHz);

    /**
     * Gets the current center frequency.
     * @param nativeHandle native HackRF handle
     * @return center frequency in Hertz
     */
    public static native long getFrequency(long nativeHandle);

    /**
     * Sets the sample rate.
     * @param nativeHandle native HackRF handle
     * @param sampleRateHz sample rate in Hertz
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int setSampleRate(long nativeHandle, long sampleRateHz);

    /**
     * Gets the current sample rate.
     * @param nativeHandle native HackRF handle
     * @return sample rate in Hertz
     */
    public static native long getSampleRate(long nativeHandle);

    /**
     * Sets the logical SDR gain.
     *
     * <p>The Java implementation maps the generic SDR gain abstraction
     * to the HackRF RX VGA/baseband gain.</p>
     * @param nativeHandle native HackRF handle
     * @param gainDb       gain in decibels
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int setGain(long nativeHandle, int gainDb);

    /**
     * Gets the logical SDR gain.
     * @param nativeHandle native HackRF handle
     * @return gain in decibels
     */
    public static native int getGain(long nativeHandle);

    /**
     * Sets the HackRF RX LNA gain.
     * @param nativeHandle native HackRF handle
     * @param gainDb       LNA gain in dB
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int setLnaGain(long nativeHandle, int gainDb);

    /**
     * Gets the HackRF RX LNA gain.
     * @param nativeHandle native HackRF handle
     * @return LNA gain in dB
     */
    public static native int getLnaGain(long nativeHandle);

    /**
     * Sets the HackRF RX VGA/baseband gain.
     * @param nativeHandle native HackRF handle
     * @param gainDb       VGA gain in dB
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int setVgaGain(long nativeHandle, int gainDb);

    /**
     * Gets the HackRF RX VGA/baseband gain.
     * @param nativeHandle native HackRF handle
     * @return VGA gain in dB
     */
    public static native int getVgaGain(long nativeHandle);

    /**
     * Enables or disables the HackRF RF amplifier.
     * @param nativeHandle native HackRF handle
     * @param enabled      {@code true} to enable the amplifier
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int setAmpEnabled(long nativeHandle, boolean enabled);

    /**
     * Checks whether the HackRF RF amplifier is enabled.
     * @param nativeHandle native HackRF handle
     * @return {@code true} if enabled
     */
    public static native boolean isAmpEnabled(long nativeHandle);

    /**
     * Starts RX streaming.
     * @param nativeHandle native HackRF handle
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int startRx(long nativeHandle);

    /**
     * Stops RX streaming.
     * @param nativeHandle native HackRF handle
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int stopRx(long nativeHandle);

    /**
     * Starts TX streaming.
     * @param nativeHandle native HackRF handle
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int startTx(long nativeHandle);

    /**
     * Stops TX streaming.
     * @param nativeHandle native HackRF handle
     * @return {@code 0} on success, negative HackRF error code on failure
     */
    public static native int stopTx(long nativeHandle);

    /**
     * Gets the current native streaming state.
     * @param nativeHandle native HackRF handle
     * @return one of the {@code STREAMING_*} constants
     */
    public static native int getStreamingState(long nativeHandle);
}
