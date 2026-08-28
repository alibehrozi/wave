package com.github.alibehrozi.wave.microdsp.hardware.sdr.drivers.hackrf;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.*;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * Represents a connected HackRF SDR device.
 *
 * <p>This class implements the generic {@link SdrDevice} abstraction while
 * delegating HackRF-specific hardware operations to {@link HackRfNative}.</p>
 *
 * <p>The Android {@link UsbDeviceConnection} is owned by the USB connection
 * layer. This class keeps a reference to it so the underlying file descriptor
 * remains valid while the native HackRF handle is active, but this class must
 * not close the USB connection directly.</p>
 *
 * <p>HackRF supports RX and TX streaming as separate operating modes.
 * Simultaneous RX/TX operation is not exposed by this implementation.</p>
 */
public final class HackRfDevice implements SdrDevice {

    private static final String TAG = "HackRfDevice";

    /**
     * Minimum supported HackRF tuning frequency.
     */
    public static final long MIN_FREQUENCY_HZ = 1_000_000L;

    /**
     * Maximum supported HackRF tuning frequency.
     */
    public static final long MAX_FREQUENCY_HZ = 6_000_000_000L;

    /**
     * Minimum supported HackRF sample rate.
     */
    public static final long MIN_SAMPLE_RATE_HZ = 2_000_000L;

    /**
     * Maximum supported HackRF sample rate.
     */
    public static final long MAX_SAMPLE_RATE_HZ = 20_000_000L;

    /**
     * Minimum HackRF RX LNA gain.
     */
    public static final int MIN_LNA_GAIN_DB = 0;

    /**
     * Maximum HackRF RX LNA gain.
     */
    public static final int MAX_LNA_GAIN_DB = 40;

    /**
     * HackRF RX LNA gain step.
     */
    public static final int LNA_GAIN_STEP_DB = 8;

    /**
     * Minimum HackRF RX VGA/baseband gain.
     */
    public static final int MIN_VGA_GAIN_DB = 0;

    /**
     * Maximum HackRF RX VGA/baseband gain.
     */
    public static final int MAX_VGA_GAIN_DB = 62;

    /**
     * HackRF RX VGA/baseband gain step.
     */
    public static final int VGA_GAIN_STEP_DB = 2;

    /*
     * Error returned when this SDR has already been closed.
     */
    public static final int ERROR_CLOSED = -3001;

    /*
     * Error returned for an invalid local parameter.
     */
    public static final int ERROR_INVALID_PARAMETER = -3002;

    /*
     * Error returned when an operation is incompatible with the current
     * streaming mode.
     */
    public static final int ERROR_STREAMING_CONFLICT = -3003;

    private final Object stateLock = new Object();

    private final UsbDevice usbDevice;
    private final UsbDeviceConnection usbConnection;
    private final SdrDeviceInfo deviceInfo;

    /**
     * The native HackRF handle. Zero means no valid handle.
     * Reads/writes are always performed inside {@code stateLock} or during
     * single-threaded construction, so {@code volatile} is sufficient to
     * make the initial write visible to all threads that check {@code closed}
     * before acquiring the lock.
     */
    private volatile long nativeHandle;

    /**
     * Current RX/TX streaming state.
     * Always accessed under {@link #stateLock} — not volatile.
     */
    private SdrStreamingState streamingState = SdrStreamingState.IDLE;

    /**
     * True once {@link #close()} has been called.
     * Always accessed under {@link #stateLock} — not volatile.
     */
    private boolean closed;

    private HackRfDevice(
            @NonNull UsbDevice usbDevice,
            @NonNull UsbDeviceConnection usbConnection,
            @NonNull SdrDeviceInfo deviceInfo,
            long nativeHandle) {

        this.usbDevice = usbDevice;
        this.usbConnection = usbConnection;
        this.deviceInfo = deviceInfo;
        this.nativeHandle = nativeHandle;
    }

    /**
     * Creates a HackRF SDR from an already opened Android USB connection.
     *
     * <p>USB permission must already be granted and the connection must
     * be valid before calling this method.</p>
     * @param device     USB HackRF device
     * @param connection opened Android USB connection
     * @return initialized HackRF SDR, or {@code null} if initialization fails
     */
    @Nullable
    public static HackRfDevice create(
            @NonNull UsbDevice device,
            @NonNull UsbDeviceConnection connection) {

        int fileDescriptor = connection.getFileDescriptor();

        /*
         * Android returns a negative file descriptor when the connection
         * is no longer valid.
         */
        if (fileDescriptor < 0) {
            Log.e(TAG, "Invalid Android USB file descriptor for: " + device.getDeviceName());
            return null;
        }

        final long nativeHandle;

        try {

            /*
             * The native bridge creates the HackRF handle from the
             * already authorized Android USB connection.
             */
            nativeHandle = HackRfNative.create(fileDescriptor);

        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "HackRF native library is not available", e);
            return null;
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to create native HackRF handle", e);
            return null;
        }

        if (nativeHandle == 0L) {
            Log.e(TAG, "Native HackRF initialization failed: " + device.getDeviceName());
            return null;
        }

        SdrDeviceInfo deviceInfo = null;
        HackRFInfo info = null;

        try {
            // Create the info object from the native handle
            info = new HackRFInfo(nativeHandle);

            // Gather device metadata
            String manufacturer = "Great Scott Gadgets";
            String product = info.getBoardIDName();
            String firmwareVersion = info.getFirmwareVersion();
            String hardwareRevision = "Rev " + info.getBoardRevision();
            String serialNumber = info.getPartIDString();

            // Build the SdrDeviceInfo object
            deviceInfo = new SdrDeviceInfo(
                    manufacturer,
                    product,
                    hardwareRevision,
                    firmwareVersion,
                    serialNumber,
                    device.getVendorId(),
                    device.getProductId(),
                    SdrRole.HALF_DUPLEX
            );

        } catch (Exception e) {
            Log.e(TAG, "Failed to read HackRF device information", e);
            // Clean up native resources and return null
            HackRfNative.close(nativeHandle);
            return null;
        } finally {
            if (info != null) {
                try {
                    info.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing HackRFInfo", e);
                }
            }
        }

        HackRfDevice sdr = new HackRfDevice(
                device,
                connection,
                deviceInfo,
                nativeHandle
        );

        /*
         * Verify that the native layer considers the device connected.
         */
        if (!sdr.isConnected()) {
            sdr.close();
            Log.e(TAG, "Native HackRF handle is not connected: " + device.getDeviceName());
            return null;
        }

        Log.i(TAG, "HackRF SDR initialized: " + device.getDeviceName());
        return sdr;
    }

    /**
     * Checks whether the HackRF is currently connected and usable.
     * @return {@code true} if the device is connected and usable
     */
    @Override
    public boolean isConnected() {

        // Read the handle under stateLock so we cannot observe a handle
        // that close() has already freed via HackRfNative.close().
        final long handle;
        synchronized (stateLock) {
            if (closed || nativeHandle == 0L) {
                return false;
            }
            handle = nativeHandle;
        }

        try {
            return HackRfNative.isConnected(handle);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error checking HackRF connection", e);
            return false;
        }
    }

    /**
     * Closes the HackRF and releases native resources.
     *
     * <p>Active RX or TX streaming is stopped before the native
     * HackRF handle is released.</p>
     *
     * <p>The Android {@link UsbDeviceConnection} is not closed here.
     * The USB connection controller owns that resource.</p>
     */
    @Override
    public void close() {
        synchronized (stateLock) {

            if (closed) {
                return;
            }

            closed = true;
            long handle = nativeHandle;
            nativeHandle = 0L;

            /*
             * Stop any active stream before closing the native device.
             */
            if (handle != 0L) {

                try {

                    switch (streamingState) {

                        case RX:
                            HackRfNative.stopRx(handle);
                            break;
                        case TX:
                            HackRfNative.stopTx(handle);
                            break;
                        case RX_TX:

                            /*
                             * RX_TX is not supported by HackRF.
                             * This case is kept defensively for future
                             * native implementations.
                             */
                            HackRfNative.stopRx(handle);
                            HackRfNative.stopTx(handle);

                            break;

                        case IDLE:
                        default:
                            break;
                    }

                } catch (RuntimeException e) {
                    Log.e(TAG, "Error stopping HackRF stream during close", e);
                }

                try {
                    HackRfNative.close(handle);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Error closing native HackRF handle", e);
                }
            }

            streamingState = SdrStreamingState.IDLE;
        }

        Log.i(TAG, "HackRF SDR closed: " + usbDevice.getDeviceName());
    }

    /**
     * Returns static identification and hardware information.
     * @return HackRF device information
     */
    @Override
    @NonNull
    public SdrDeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    /**
     * Performs a HackRF hardware reset.
     *
     * <p>A reset stops active streaming and returns the local streaming
     * state to IDLE when successful.</p>
     * @return {@code 0} on success, negative error code on failure
     */
    @Override
    public int reset() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            int result = HackRfNative.reset(nativeHandle);
            if (result == 0) {
                streamingState = SdrStreamingState.IDLE;
            }

            return result;
        }
    }

    /**
     * Sets the HackRF center frequency.
     * @param frequencyHz center frequency in Hertz
     * @return {@code 0} on success, negative error code on failure
     */
    @Override
    public int setFrequency(long frequencyHz) {

        if (frequencyHz < MIN_FREQUENCY_HZ || frequencyHz > MAX_FREQUENCY_HZ) {
            return ERROR_INVALID_PARAMETER;
        }

        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            return HackRfNative.setFrequency(
                    nativeHandle,
                    frequencyHz
            );
        }
    }

    /**
     * Gets the currently configured center frequency.
     * @return center frequency in Hertz
     */
    @Override
    public long getFrequency() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return 0L;
            }

            return HackRfNative.getFrequency(nativeHandle);
        }
    }

    /**
     * Sets the HackRF sample rate.
     * @param rateHz sample rate in Hertz
     * @return {@code 0} on success, negative error code on failure
     */
    @Override
    public int setSampleRate(long rateHz) {

        if (rateHz < MIN_SAMPLE_RATE_HZ || rateHz > MAX_SAMPLE_RATE_HZ) {
            return ERROR_INVALID_PARAMETER;
        }

        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            return HackRfNative.setSampleRate(nativeHandle, rateHz);
        }
    }

    /**
     * Gets the currently configured sample rate.
     * @return sample rate in Hertz
     */
    @Override
    public long getSampleRate() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return 0L;
            }

            return HackRfNative.getSampleRate(nativeHandle);
        }
    }

    /**
     * Sets the logical SDR gain.
     *
     * <p>For HackRF, the generic gain abstraction is mapped to the
     * RX VGA/baseband gain. Use {@link #setLnaGain(int)} when the
     * RF/IF gain stage needs direct control.</p>
     * @param gainDb gain in decibels
     * @return {@code 0} on success, negative error code on failure
     */
    @Override
    public int setGain(int gainDb) {
        return setVgaGain(gainDb);
    }

    /**
     * Gets the logical SDR gain.
     * @return gain in decibels
     */
    @Override
    public int getGain() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return 0;
            }

            return HackRfNative.getGain(
                    nativeHandle
            );
        }
    }

    /**
     * Sets the HackRF RX LNA gain.
     * @param gainDb LNA gain in dB
     * @return {@code 0} on success, negative error code on failure
     */
    public int setLnaGain(int gainDb) {

        if (!isValidLnaGain(gainDb)) {
            return ERROR_INVALID_PARAMETER;
        }

        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            return HackRfNative.setLnaGain(nativeHandle, gainDb);
        }
    }

    /**
     * Gets the HackRF RX LNA gain.
     * @return LNA gain in dB
     */
    public int getLnaGain() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return 0;
            }

            return HackRfNative.getLnaGain(nativeHandle);
        }
    }

    /**
     * Sets the HackRF RX VGA/baseband gain.
     * @param gainDb VGA gain in dB
     * @return {@code 0} on success, negative error code on failure
     */
    public int setVgaGain(int gainDb) {

        if (!isValidVgaGain(gainDb)) {
            return ERROR_INVALID_PARAMETER;
        }

        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            return HackRfNative.setVgaGain(nativeHandle, gainDb);
        }
    }

    /**
     * Gets the HackRF RX VGA/baseband gain.
     * @return VGA gain in dB
     */
    public int getVgaGain() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return 0;
            }

            return HackRfNative.getVgaGain(nativeHandle);
        }
    }

    /**
     * Enables or disables the HackRF RF amplifier.
     * @param enabled {@code true} to enable the amplifier
     * @return {@code 0} on success, negative error code on failure
     */
    public int setAmpEnabled(boolean enabled) {
        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            return HackRfNative.setAmpEnabled(nativeHandle, enabled);
        }
    }

    /**
     * Checks whether the HackRF RF amplifier is enabled.
     * @return {@code true} if enabled
     */
    public boolean isAmpEnabled() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return false;
            }

            return HackRfNative.isAmpEnabled(nativeHandle);
        }
    }

    /**
     * Starts receiving IQ samples from the HackRF.
     *
     * <p>HackRF cannot receive and transmit simultaneously, so RX
     * cannot be started while TX is active.</p>
     * @return {@code 0} on success, negative error code on failure
     */
    @Override
    public int startRx() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            if (streamingState == SdrStreamingState.TX) {
                return ERROR_STREAMING_CONFLICT;
            }

            if (streamingState == SdrStreamingState.RX) {
                return 0;
            }

            int result = HackRfNative.startRx(nativeHandle);

            if (result == 0) {
                streamingState = SdrStreamingState.RX;
            }

            return result;
        }
    }

    /**
     * Stops receiving IQ samples.
     * @return {@code 0} on success, negative error code on failure
     */
    @Override
    public int stopRx() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            if (streamingState != SdrStreamingState.RX) {
                return 0;
            }

            int result = HackRfNative.stopRx(nativeHandle);
            if (result == 0) {
                streamingState = SdrStreamingState.IDLE;
            }

            return result;
        }
    }

    /**
     * Starts transmitting IQ samples to the HackRF.
     *
     * <p>HackRF cannot receive and transmit simultaneously, so TX
     * cannot be started while RX is active.</p>
     * @return {@code 0} on success, negative error code on failure
     */
    @Override
    public int startTx() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            if (streamingState == SdrStreamingState.RX) {
                return ERROR_STREAMING_CONFLICT;
            }

            if (streamingState == SdrStreamingState.TX) {
                return 0;
            }

            int result = HackRfNative.startTx(nativeHandle);
            if (result == 0) {
                streamingState = SdrStreamingState.TX;
            }

            return result;
        }
    }

    /**
     * Stops transmitting IQ samples.
     * @return {@code 0} on success, negative error code on failure
     */
    @Override
    public int stopTx() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return ERROR_CLOSED;
            }

            if (streamingState != SdrStreamingState.TX) {
                return 0;
            }

            int result = HackRfNative.stopTx(nativeHandle);

            if (result == 0) {
                streamingState = SdrStreamingState.IDLE;
            }

            return result;
        }
    }

    /**
     * Returns the current RX/TX streaming state.
     * @return current streaming state
     */
    @Override
    @NonNull
    public SdrStreamingState getStreamingState() {
        synchronized (stateLock) {

            if (!isOperational()) {
                return SdrStreamingState.IDLE;
            }

            int nativeState = HackRfNative.getStreamingState(nativeHandle);

            switch (nativeState) {
                case HackRfNative.STREAMING_RX:
                    streamingState = SdrStreamingState.RX;
                    break;
                case HackRfNative.STREAMING_TX:
                    streamingState = SdrStreamingState.TX;
                    break;
                case HackRfNative.STREAMING_IDLE:
                default:
                    streamingState = SdrStreamingState.IDLE;
                    break;
            }

            return streamingState;
        }
    }

    /**
     * Returns the native HackRF handle.
     *
     * <p>Returns {@code 0} if the device has been closed or no valid handle
     * exists. Callers must not use the handle after it has become zero.</p>
     * @return native handle, or {@code 0} if no valid handle exists
     */
    @Override
    public long getNativeHandle() {
        synchronized (stateLock) {
            return closed ? 0L : nativeHandle;
        }
    }

    /**
     * Returns the Android USB device used by this SDR.
     * @return USB device
     */
    @NonNull
    public UsbDevice getUsbDevice() {
        return usbDevice;
    }

    /**
     * Returns the Android USB connection owned by the USB controller.
     *
     * <p>The returned connection must not be closed by callers.</p>
     * @return Android USB connection
     */
    @NonNull
    public UsbDeviceConnection getUsbConnection() {
        return usbConnection;
    }

    /**
     * Checks whether the SDR can currently execute native operations.
     * @return {@code true} if operational
     */
    private boolean isOperational() {
        long handle = nativeHandle;
        return !closed && handle != 0L;
    }

    /**
     * Validates a HackRF LNA gain value.
     * @param gainDb gain in dB
     * @return {@code true} if valid
     */
    private boolean isValidLnaGain(int gainDb) {
        return gainDb >= MIN_LNA_GAIN_DB
                && gainDb <= MAX_LNA_GAIN_DB
                && gainDb % LNA_GAIN_STEP_DB == 0;
    }

    /**
     * Validates a HackRF VGA gain value.
     * @param gainDb gain in dB
     * @return {@code true} if valid
     */
    private boolean isValidVgaGain(int gainDb) {
        return gainDb >= MIN_VGA_GAIN_DB
                && gainDb <= MAX_VGA_GAIN_DB
                && gainDb % VGA_GAIN_STEP_DB == 0;
    }
}