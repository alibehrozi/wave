package com.github.alibehrozi.wave.microdsp.hardware.usb.connection;

import android.annotation.SuppressLint;
import android.hardware.usb.UsbDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Represents the state of a USB connection.
 *
 * <p>This class tracks:
 * connection state, device information, error information,
 * timestamps, connection attempts, and auto-connect status.</p>
 *
 * <p>The state can be copied using {@link #copy()} when a snapshot
 * of the current state is required.</p>
 */
public class UsbConnectionState {

    /**
     * Possible connection states.
     */
    public enum State {

        /**
         * No device is connected.
         */
        DISCONNECTED,

        /**
         * Device is being connected.
         */
        CONNECTING,

        /**
         * Device is connected and ready to use.
         */
        CONNECTED,

        /**
         * Device is being disconnected.
         */
        DISCONNECTING,

        /**
         * Device connection failed due to an error.
         */
        ERROR,

        /**
         * Attempting to reconnect.
         */
        RECONNECTING,

        /**
         * Connection is temporarily paused.
         */
        PAUSED
    }

    /**
     * Possible connection error types.
     */
    public enum ErrorType {

        /**
         * No error.
         */
        NONE,

        /**
         * USB permission was denied.
         */
        PERMISSION_DENIED,

        /**
         * USB device could not be found.
         */
        DEVICE_NOT_FOUND,

        /**
         * General USB error.
         */
        USB_ERROR,

        /**
         * USB device could not be opened.
         */
        OPEN_FAILED,

        /**
         * Operation timed out.
         */
        TIMEOUT,

        /**
         * Device was disconnected unexpectedly.
         */
        DISCONNECTED,

        /**
         * Unknown error.
         */
        UNKNOWN
    }

    // Connection state
    private State state = State.DISCONNECTED;
    private State previousState = State.DISCONNECTED;

    // Device information
    private UsbDevice device;
    private String deviceName;
    private int vendorId;
    private int productId;

    // Error information
    private ErrorType errorType = ErrorType.NONE;
    private int errorCode = 0;
    private String errorMessage;
    private Throwable errorCause;

    // State timestamps
    private long stateChangedAt = System.currentTimeMillis();
    private long connectedAt = 0;
    private long disconnectedAt = 0;

    // Connection metadata
    private int connectionAttempts = 0;
    private int reconnectionAttempts = 0;
    private boolean isAutoConnect = false;


    /**
     * Creates a new USB connection state.
     */
    public UsbConnectionState() {
        // Default state is DISCONNECTED.
    }


    /**
     * Creates a USB connection state for the specified device.
     * @param device USB device
     */
    public UsbConnectionState(@NonNull UsbDevice device) {
        setDevice(device);
    }


    /**
     * Sets the current connection state.
     *
     * <p>When the state changes, the previous state and state-change
     * timestamp are updated.</p>
     *
     * <p>When the state becomes {@link State#CONNECTED}, the connection
     * timestamp is updated.</p>
     *
     * <p>When the state becomes {@link State#DISCONNECTED}, the
     * disconnection timestamp is updated.</p>
     * @param newState new connection state
     */
    public synchronized void setState(@NonNull State newState) {
        if (this.state == newState) {
            return;
        }

        this.previousState = this.state;
        this.state = newState;
        this.stateChangedAt = System.currentTimeMillis();

        if (newState == State.CONNECTED) {
            this.connectedAt = this.stateChangedAt;
            this.disconnectedAt = 0;
        } else if (newState == State.DISCONNECTED) {
            this.disconnectedAt = this.stateChangedAt;
        }
    }


    /**
     * Gets the current connection state.
     * @return current connection state
     */
    @NonNull
    public synchronized State getState() {
        return state;
    }


    /**
     * Gets the previous connection state.
     * @return previous connection state
     */
    @NonNull
    public synchronized State getPreviousState() {
        return previousState;
    }


    /**
     * Checks whether the device is currently connected.
     * @return {@code true} if the state is CONNECTED
     */
    public synchronized boolean isConnected() {
        return state == State.CONNECTED;
    }


    /**
     * Checks whether the connection is currently being established.
     * @return {@code true} if the state is CONNECTING or RECONNECTING
     */
    public synchronized boolean isConnecting() {
        return state == State.CONNECTING
                || state == State.RECONNECTING;
    }


    /**
     * Checks whether the connection is currently disconnected.
     * @return {@code true} if the state is DISCONNECTED
     */
    public synchronized boolean isDisconnected() {
        return state == State.DISCONNECTED;
    }


    /**
     * Checks whether the connection is currently in an error state.
     * @return {@code true} if the state is ERROR
     */
    public synchronized boolean isError() {
        return state == State.ERROR;
    }


    /**
     * Checks whether the connection is currently active.
     *
     * <p>An active connection includes CONNECTED, CONNECTING,
     * and RECONNECTING states.</p>
     * @return {@code true} if the connection is active
     */
    public synchronized boolean isActive() {
        return state == State.CONNECTED || state == State.CONNECTING || state == State.RECONNECTING;
    }


    /**
     * Sets the current USB device.
     *
     * <p>When a device is assigned, its device name, vendor ID,
     * and product ID are stored in the state.</p>
     *
     * <p>When {@code null} is supplied, all device information
     * is cleared.</p>
     * @param device current USB device, or {@code null}
     */
    public synchronized void setDevice(@Nullable UsbDevice device) {
        this.device = device;
        if (device != null) {
            this.deviceName = device.getDeviceName();
            this.vendorId = device.getVendorId();
            this.productId = device.getProductId();
        } else {
            this.deviceName = null;
            this.vendorId = 0;
            this.productId = 0;
        }
    }


    /**
     * Gets the current USB device.
     * @return current USB device, or {@code null}
     */
    @Nullable
    public synchronized UsbDevice getDevice() {
        return device;
    }


    /**
     * Gets the current USB device name.
     * @return device name, or {@code null}
     */
    @Nullable
    public synchronized String getDeviceName() {
        return deviceName;
    }


    /**
     * Gets the current USB vendor ID.
     * @return USB vendor ID
     */
    public synchronized int getVendorId() {
        return vendorId;
    }


    /**
     * Gets the current USB product ID.
     * @return USB product ID
     */
    public synchronized int getProductId() {
        return productId;
    }


    /**
     * Gets a display string for the current USB device.
     * @return device display string
     */
    @NonNull
    public synchronized String getDeviceDisplayString() {

        if (deviceName == null) {
            return "Unknown Device";
        }

        return deviceName
                + " (VID:0x"
                + Integer.toHexString(vendorId)
                + " PID:0x"
                + Integer.toHexString(productId)
                + ")";
    }


    /**
     * Sets an error on the connection state.
     *
     * <p>Setting an error also changes the connection state
     * to {@link State#ERROR}.</p>
     * @param errorType    error type
     * @param errorCode    error code
     * @param errorMessage error message
     * @param cause        optional error cause
     */
    public synchronized void setError(@NonNull ErrorType errorType, int errorCode,
                                      @NonNull String errorMessage, @Nullable Throwable cause) {
        this.errorType = errorType;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorCause = cause;
        setState(State.ERROR);
    }


    /**
     * Sets an error on the connection state.
     * @param errorType    error type
     * @param errorCode    error code
     * @param errorMessage error message
     */
    public synchronized void setError(@NonNull ErrorType errorType, int errorCode,
                                      @NonNull String errorMessage) {
        setError(errorType, errorCode, errorMessage, null);
    }


    /**
     * Clears the current error information.
     *
     * <p>This method only clears the stored error information.
     * It does not automatically change the current connection state.</p>
     */
    public synchronized void clearError() {
        this.errorType = ErrorType.NONE;
        this.errorCode = 0;
        this.errorMessage = null;
        this.errorCause = null;
    }


    /**
     * Gets the current error type.
     * @return current error type
     */
    @NonNull
    public synchronized ErrorType getErrorType() {
        return errorType;
    }


    /**
     * Gets the current error code.
     * @return error code
     */
    public synchronized int getErrorCode() {
        return errorCode;
    }


    /**
     * Gets the current error message.
     * @return error message, or {@code null}
     */
    @Nullable
    public synchronized String getErrorMessage() {
        return errorMessage;
    }


    /**
     * Gets the current error cause.
     * @return error cause, or {@code null}
     */
    @Nullable
    public synchronized Throwable getErrorCause() {
        return errorCause;
    }


    /**
     * Checks whether an error is currently stored.
     * @return {@code true} if an error exists
     */
    public synchronized boolean hasError() {
        return errorType != ErrorType.NONE;
    }


    /**
     * Gets the timestamp when the state was last changed.
     * @return state change timestamp in milliseconds
     */
    public synchronized long getStateChangedAt() {
        return stateChangedAt;
    }


    /**
     * Gets the timestamp when the device became connected.
     * @return connection timestamp in milliseconds, or {@code 0}
     */
    public synchronized long getConnectedAt() {
        return connectedAt;
    }


    /**
     * Gets the timestamp when the device became disconnected.
     * @return disconnection timestamp in milliseconds, or {@code 0}
     */
    public synchronized long getDisconnectedAt() {
        return disconnectedAt;
    }


    /**
     * Gets the duration of the current connection.
     *
     * <p>The duration is calculated only while the current state is
     * {@link State#CONNECTED}.</p>
     * @return connected duration in milliseconds
     */
    public synchronized long getConnectedDuration() {

        if (state != State.CONNECTED || connectedAt == 0) {
            return 0;
        }

        return System.currentTimeMillis() - connectedAt;
    }


    /**
     * Gets a formatted string representing the current
     * connection duration.
     * @return formatted connection duration
     */
    @SuppressLint("DefaultLocale")
    @NonNull
    public synchronized String getConnectedDurationString() {
        long duration = getConnectedDuration();
        if (duration == 0) return "Not connected";

        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        long hours = minutes / 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }


    /**
     * Increments the connection attempt counter.
     */
    public synchronized void incrementConnectionAttempts() {
        connectionAttempts++;
    }


    /**
     * Gets the number of connection attempts.
     * @return number of connection attempts
     */
    public synchronized int getConnectionAttempts() {
        return connectionAttempts;
    }


    /**
     * Increments the reconnection attempt counter.
     */
    public synchronized void incrementReconnectionAttempts() {
        reconnectionAttempts++;
    }


    /**
     * Gets the number of reconnection attempts.
     * @return number of reconnection attempts
     */
    public synchronized int getReconnectionAttempts() {
        return reconnectionAttempts;
    }


    /**
     * Resets connection and reconnection attempt counters.
     */
    public synchronized void resetAttempts() {
        connectionAttempts = 0;
        reconnectionAttempts = 0;
    }


    /**
     * Sets whether auto-connect is enabled.
     * @param enabled {@code true} to enable auto-connect
     */
    public synchronized void setAutoConnect(boolean enabled) {
        isAutoConnect = enabled;
    }


    /**
     * Checks whether auto-connect is enabled.
     * @return {@code true} if auto-connect is enabled
     */
    public synchronized boolean isAutoConnect() {
        return isAutoConnect;
    }


    /**
     * Resets the state to its default disconnected state.
     *
     * <p>This clears the current device, error information,
     * attempt counters, and connection timestamps.</p>
     */
    public synchronized void reset() {

        /*
         * Clear device information.
         */
        setDevice(null);

        /*
         * Clear error information.
         */
        clearError();

        /*
         * Reset attempt counters.
         */
        resetAttempts();

        /*
         * Reset connection timestamps.
         */
        connectedAt = 0;
        disconnectedAt = 0;

        /*
         * Reset state information.
         */
        previousState = State.DISCONNECTED;
        state = State.DISCONNECTED;
        stateChangedAt = System.currentTimeMillis();
    }


    /**
     * Creates a copy of the current connection state.
     *
     * <p>The returned object is an independent state snapshot.
     * Changes made to the copy do not affect the original state.</p>
     * @return copied connection state
     */
    @NonNull
    public synchronized UsbConnectionState copy() {
        UsbConnectionState copy = new UsbConnectionState();
        copy.state = this.state;
        copy.previousState = this.previousState;
        copy.device = this.device;
        copy.deviceName = this.deviceName;
        copy.vendorId = this.vendorId;
        copy.productId = this.productId;
        copy.errorType = this.errorType;
        copy.errorCode = this.errorCode;
        copy.errorMessage = this.errorMessage;
        copy.errorCause = this.errorCause;
        copy.stateChangedAt = this.stateChangedAt;
        copy.connectedAt = this.connectedAt;
        copy.disconnectedAt = this.disconnectedAt;
        copy.connectionAttempts = this.connectionAttempts;
        copy.reconnectionAttempts = this.reconnectionAttempts;
        copy.isAutoConnect = this.isAutoConnect;
        return copy;
    }


    /**
     * Gets a human-readable status string.
     * @return connection status
     */
    @NonNull
    public synchronized String getStatusString() {

        StringBuilder sb = new StringBuilder();

        sb.append("State: ").append(state.name());

        if (deviceName != null) {
            sb.append(" | Device: ").append(deviceName);
        }

        if (hasError()) {
            sb.append(" | Error: ").append(errorType.name());
            if (errorMessage != null) {
                sb.append(" - ").append(errorMessage);
            }
        }

        if (state == State.CONNECTED) {
            sb.append(" | Duration: ").append(getConnectedDurationString());
        }

        return sb.toString();
    }


    @Override
    public synchronized String toString() {
        return getStatusString();
    }


    @Override
    public synchronized boolean equals(
            Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof UsbConnectionState)) {
            return false;
        }

        UsbConnectionState other = (UsbConnectionState) obj;
        return state == other.state &&
                previousState == other.previousState &&
                vendorId == other.vendorId &&
                productId == other.productId &&
                errorType == other.errorType &&
                errorCode == other.errorCode &&
                connectionAttempts == other.connectionAttempts &&
                reconnectionAttempts == other.reconnectionAttempts &&
                isAutoConnect == other.isAutoConnect &&
                Objects.equals(deviceName, other.deviceName) &&
                Objects.equals(errorMessage, other.errorMessage);
    }


    @Override
    public synchronized int hashCode() {

        int result = state.hashCode();

        result = 31 * result + previousState.hashCode();
        result = 31 * result + (deviceName != null ? deviceName.hashCode() : 0);
        result = 31 * result + vendorId;
        result = 31 * result + productId;
        result = 31 * result + errorType.hashCode();
        result = 31 * result + errorCode;
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        result = 31 * result + connectionAttempts;
        result = 31 * result + reconnectionAttempts;
        result = 31 * result + (isAutoConnect ? 1 : 0);

        return result;
    }
}