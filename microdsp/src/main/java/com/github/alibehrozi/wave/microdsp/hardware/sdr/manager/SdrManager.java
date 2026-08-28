package com.github.alibehrozi.wave.microdsp.hardware.sdr.manager;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.*;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.alibehrozi.wave.microdsp.hardware.usb.connection.UsbConnectionController;
import com.github.alibehrozi.wave.microdsp.hardware.usb.connection.UsbConnectionState;
import com.github.alibehrozi.wave.microdsp.hardware.usb.core.UsbDeviceSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates Software Defined Radio (SDR) hardware lifecycle and multi-device
 * sessions.
 *
 * <p>
 * This facade orchestrates driver management, session routing, state tracking,
 * and main-thread
 * event notification.
 * </p>
 */
public final class SdrManager implements AutoCloseable {

    private static final String TAG = "SdrManager";

    /**
     * Listener for SDR hardware connection, disconnection, and error events.
     * All callbacks are invoked on the Android main thread.
     */
    public interface Listener {
        /**
         * Called when an SDR hardware device is successfully initialized and connected.
         * @param sdr    the initialized SDR device instance
         * @param device the underlying USB device
         */
        void onSdrConnected(@NonNull SdrDevice sdr, @NonNull UsbDevice device);

        /**
         * Called when an SDR hardware device is disconnected.
         * @param device     the disconnected USB device
         * @param deviceInfo metadata for the disconnected device, or {@code null} if unavailable
         */
        void onSdrDisconnected(@NonNull UsbDevice device, @Nullable SdrDeviceInfo deviceInfo);

        /**
         * Called when an SDR hardware or driver error occurs.
         * @param errorType categorical classification of the error
         * @param message   descriptive error message, or {@code null}
         */
        default void onError(@NonNull SdrErrorType errorType, @Nullable String message) {
        }
    }

    private final UsbConnectionController usbController;
    private final List<SdrDriver> drivers = new CopyOnWriteArrayList<>();
    private final SdrSessionRegistry sessionRegistry = new SdrSessionRegistry();
    private final SdrEventNotifier eventNotifier = new SdrEventNotifier();

    private volatile SdrState state = SdrState.DISCONNECTED;

    private final ExecutorService managerExecutor = Executors.newSingleThreadExecutor();
    private final Object sdrLock = new Object();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicLong lifecycleGeneration = new AtomicLong(0);

    private final UsbConnectionController.ConnectionListener usbListener = new UsbConnectionController.ConnectionListener() {
        @Override
        public void onDeviceConnected(@NonNull UsbDevice device, @NonNull UsbDeviceConnection connection) {
            executeManagerTask(() -> handleUsbConnected(device, connection));
        }

        @Override
        public void onDeviceDisconnected(@NonNull UsbDevice device) {
            executeManagerTask(() -> handleUsbDisconnected(device));
        }

        @Override
        public void onStateChanged(@NonNull UsbConnectionState usbState) {
            executeManagerTask(() -> handleUsbStateChanged(usbState));
        }

        @Override
        public void onError(@NonNull UsbConnectionState usbState) {
            executeManagerTask(() -> handleUsbError(usbState));
        }
    };

    /**
     * Constructs an {@code SdrManager} using default USB management components for the given context.
     * @param context the Android application context
     */
    public SdrManager(@NonNull Context context) {
        this(new UsbConnectionController(context));
    }

    /**
     * Constructs an {@code SdrManager} with an externally supplied USB connection controller.
     * @param usbController the USB connection controller to use
     */
    public SdrManager(@NonNull UsbConnectionController usbController) {
        this.usbController = usbController;
        this.usbController.addListener(usbListener);
        Log.d(TAG, "SdrManager created");
    }

    /**
     * Registers a new SDR driver with the manager.
     * @param driver the SDR driver to register
     */
    public void registerDriver(@NonNull SdrDriver driver) {
        checkNotClosed();
        if (!drivers.contains(driver)) {
            drivers.add(driver);
            Log.d(TAG, "SDR driver registered: " + getDriverName(driver));
        }
    }

    /**
     * Unregisters an existing SDR driver and disconnects any active sessions using
     * it.
     * @param driver the SDR driver to unregister
     */
    public void unregisterDriver(@NonNull SdrDriver driver) {
        checkNotClosed();
        for (SdrSession session : sessionRegistry.getActiveSessions()) {
            if (session.getDriver().equals(driver)) {
                disconnect(session.getUsbDevice());
            }
        }
        drivers.remove(driver);
        Log.d(TAG, "SDR driver removed: " + getDriverName(driver));
    }

    /**
     * Unregisters all SDR drivers and closes any active sessions.
     */
    public void clearDrivers() {
        checkNotClosed();
        disconnectAll();
        drivers.clear();
        Log.d(TAG, "All SDR drivers cleared");
    }

    /**
     * Returns a snapshot list of all currently registered SDR drivers.
     * @return list of registered SDR drivers
     */
    @NonNull
    public List<SdrDriver> getDrivers() {
        return new ArrayList<>(drivers);
    }

    /**
     * Sets multiple device specifications for allowed connections.
     * @param specs list of USB device specifications
     */
    public void setDeviceSpecs(@NonNull List<UsbDeviceSpec> specs) {
        checkNotClosed();
        usbController.setDeviceSpecs(specs);
    }

    /**
     * Sets a single device specification, clearing any previous ones.
     * @param spec the USB device specification
     */
    public void setDeviceSpec(@NonNull UsbDeviceSpec spec) {
        checkNotClosed();
        usbController.setDeviceSpec(spec);
    }

    /**
     * Adds a device specification to the allowed list.
     * @param spec the USB device specification to add
     */
    public void addDeviceSpec(@NonNull UsbDeviceSpec spec) {
        checkNotClosed();
        usbController.addDeviceSpec(spec);
    }

    /**
     * Removes a device specification from the allowed list.
     * @param spec the USB device specification to remove
     */
    public void removeDeviceSpec(@NonNull UsbDeviceSpec spec) {
        checkNotClosed();
        usbController.removeDeviceSpec(spec);
    }

    /**
     * Gets all currently configured device specifications.
     * @return a list of configured device specifications
     */
    @NonNull
    public List<UsbDeviceSpec> getDeviceSpecs() {
        return usbController.getDeviceSpecs();
    }

    /**
     * Gets the first configured device specification, if any.
     * @return the first device specification, or null if empty
     */
    @Nullable
    public UsbDeviceSpec getDeviceSpec() {
        return usbController.getDeviceSpec();
    }

    /**
     * Gets a list of all physically available USB devices that match the configured
     * specs.
     * @return a list of available USB devices
     */
    @NonNull
    public List<UsbDevice> getAvailableUsbDevices() {
        checkNotClosed();
        return usbController.getAvailableDevices();
    }

    /**
     * Enables or disables automatic connection to authorized SDR devices.
     * <p>
     * Requires at least one driver and one device spec to be registered.
     * </p>
     * @param enabled true to auto-connect, false otherwise
     */
    public void setAutoConnectEnabled(boolean enabled) {
        checkNotClosed();
        if (enabled) {
            if (usbController.getDeviceSpecs().isEmpty()) {
                throw new IllegalStateException(
                        "At least one device specification must be set before enabling auto-connect");
            }
            if (drivers.isEmpty()) {
                throw new IllegalStateException(
                        "At least one SDR driver must be registered before enabling auto-connect");
            }
        }
        usbController.setAutoConnectEnabled(enabled);
    }

    /**
     * Checks whether automatic USB SDR connection handling is enabled.
     * @return {@code true} if auto-connect is enabled, {@code false} otherwise
     */
    public boolean isAutoConnectEnabled() {
        return usbController.isAutoConnectEnabled();
    }

    /**
     * Initiates a connection to the first available authorized SDR device.
     */
    public void connect() {
        checkReadyForConnection();
        lifecycleGeneration.incrementAndGet();
        setState(SdrState.CONNECTING);
        usbController.connect();
    }

    /**
     * Initiates a connection to a specific authorized SDR device specification.
     * @param spec the device specification to connect to
     */
    public void connect(@NonNull UsbDeviceSpec spec) {
        checkReadyForConnection();
        lifecycleGeneration.incrementAndGet();
        setState(SdrState.CONNECTING);
        usbController.connect(spec);
    }

    /**
     * Initiates a connection directly to a specific USB device instance.
     * @param device the USB device to connect to
     */
    public void connect(@NonNull UsbDevice device) {
        checkReadyForConnection();
        lifecycleGeneration.incrementAndGet();
        setState(SdrState.CONNECTING);
        usbController.connect(device);
    }

    /**
     * Attempts to reconnect to the last connected device.
     */
    public void reconnect() {
        checkReadyForConnection();
        lifecycleGeneration.incrementAndGet();
        setState(SdrState.CONNECTING);
        usbController.reconnect();
    }

    /**
     * Disconnects the current active device(s).
     */
    public void disconnect() {
        disconnectAll();
    }

    /**
     * Disconnects all active SDR sessions and closes underlying USB connections.
     */
    public void disconnectAll() {
        if (isClosed.get()) {
            return;
        }
        lifecycleGeneration.incrementAndGet();
        executeManagerTask(() -> {
            if (sessionRegistry.isEmpty() && !usbController.isConnected()) {
                return;
            }
            setState(SdrState.DISCONNECTING);
            sessionRegistry.closeAll(sdrLock);
            usbController.disconnect();
        });
    }

    /**
     * Disconnects a specific active SDR session associated with the provided USB device.
     * @param device the USB device to disconnect
     */
    public void disconnect(@NonNull UsbDevice device) {
        if (isClosed.get()) {
            return;
        }
        executeManagerTask(() -> {
            SdrSession session = sessionRegistry.removeSession(device);
            if (session != null) {
                synchronized (sdrLock) {
                    safeClose(session.getDevice());
                }
                notifySdrDisconnected(device, session.getDeviceInfo());
            }
            if (sessionRegistry.isEmpty()) {
                setState(SdrState.DISCONNECTED);
            }
        });
    }

    /**
     * Gets all active SDR sessions currently managed.
     * @return a list of active sessions
     */
    @NonNull
    public List<SdrSession> getActiveSessions() {
        return sessionRegistry.getActiveSessions();
    }

    /**
     * Gets a list of all currently connected SDR devices.
     * @return a list of connected SDR devices
     */
    @NonNull
    public List<SdrDevice> getConnectedDevices() {
        return sessionRegistry.getConnectedDevices();
    }

    /**
     * Gets a list of connected SDR devices that support receiving (RX).
     * @return a list of RX-capable SDR devices
     */
    @NonNull
    public List<SdrDevice> getDevicesSupportingRx() {
        return sessionRegistry.getDevicesSupportingRx();
    }

    /**
     * Gets a list of connected SDR devices that support transmitting (TX).
     * @return a list of TX-capable SDR devices
     */
    @NonNull
    public List<SdrDevice> getDevicesSupportingTx() {
        return sessionRegistry.getDevicesSupportingTx();
    }

    /**
     * Gets the SDR device currently assigned the RX role.
     * @return the RX SDR device, or null
     */
    @Nullable
    public SdrDevice getRxDevice() {
        return sessionRegistry.getRxDevice();
    }

    /**
     * Manually assigns an SDR device to the RX role.
     * @param sdr the SDR to set as RX
     */
    public void setRxDevice(@Nullable SdrDevice sdr) {
        sessionRegistry.setRxDevice(sdr);
    }

    /**
     * Gets the SDR device currently assigned the TX role.
     * @return the TX SDR device, or null
     */
    @Nullable
    public SdrDevice getTxDevice() {
        return sessionRegistry.getTxDevice();
    }

    /**
     * Manually assigns an SDR device to the TX role.
     * @param sdr the SDR to set as TX
     */
    public void setTxDevice(@Nullable SdrDevice sdr) {
        sessionRegistry.setTxDevice(sdr);
    }

    /**
     * Sets a specific functional role (e.g. RX, TX) for a connected USB device.
     * @param device the USB device
     * @param role   the functional role
     */
    public void setRole(@NonNull UsbDevice device, @NonNull SdrRole role) {
        sessionRegistry.setRole(device, role);
    }

    /**
     * Checks whether at least one SDR device session is currently connected and active.
     * @return {@code true} if one or more SDR devices are active, {@code false} otherwise
     */
    public boolean isConnected() {
        return !sessionRegistry.isEmpty();
    }

    /**
     * Gets the current overarching state of the SDR manager.
     * @return the current state
     */
    @NonNull
    public SdrState getState() {
        return state;
    }

    /**
     * Registers a listener to receive SDR connection and error events.
     * @param listener the listener to add
     */
    public void addListener(@NonNull Listener listener) {
        eventNotifier.addListener(listener, isClosed);
    }

    /**
     * Unregisters a listener from receiving SDR lifecycle events.
     * @param listener the listener to remove
     */
    public void removeListener(@NonNull Listener listener) {
        eventNotifier.removeListener(listener);
    }

    /**
     * Removes all registered SDR lifecycle listeners.
     */
    public void clearListeners() {
        eventNotifier.clearListeners();
    }

    /**
     * Retrieves the device information for all currently connected SDR devices.
     * @return a list of device info objects for connected devices
     */
    @NonNull
    public List<SdrDeviceInfo> getDeviceInfo() {
        List<SdrDeviceInfo> infos = new ArrayList<>();
        for (SdrSession session : sessionRegistry.getActiveSessions()) {
            SdrDeviceInfo info = session.getDeviceInfo();
            if (info != null) {
                infos.add(info);
            }
        }
        return infos;
    }

    /**
     * Alias for {@link #getDeviceInfo()}, retrieving device information for all
     * connected SDR devices.
     * @return a list of device info objects for connected devices
     */
    @NonNull
    public List<SdrDeviceInfo> getDeviceInfos() {
        return getDeviceInfo();
    }

    /**
     * Retrieves the device information for a specific USB device session.
     * @param device the USB device
     * @return device info, or null if not found
     */
    @Nullable
    public SdrDeviceInfo getDeviceInfo(@NonNull UsbDevice device) {
        SdrSession session = sessionRegistry.getSession(device);
        return session != null ? session.getDeviceInfo() : null;
    }

    /**
     * Retrieves the device information for a specific SDR device instance.
     * @param sdr the SDR device instance
     * @return device info, or null if not found
     */
    @Nullable
    public SdrDeviceInfo getDeviceInfo(@NonNull SdrDevice sdr) {
        SdrSession session = sessionRegistry.findSessionForDevice(sdr);
        return session != null ? session.getDeviceInfo() : sdr.getDeviceInfo();
    }

    /**
     * Gets the native C pointers/handles for all currently connected SDR devices.
     * @return list of native handles for connected SDR devices
     */
    @NonNull
    public List<Long> getNativeHandle() {
        List<Long> handles = new ArrayList<>();
        for (SdrDevice sdr : sessionRegistry.getConnectedDevices()) {
            handles.add(sdr.getNativeHandle());
        }
        return handles;
    }

    /**
     * Alias for {@link #getNativeHandle()}, retrieving native handles for all
     * connected SDR devices.
     * @return list of native handles for connected SDR devices
     */
    @NonNull
    public List<Long> getNativeHandles() {
        return getNativeHandle();
    }

    /**
     * Gets the native C pointer/handle for a specific USB device session.
     * @param device the USB device
     * @return native handle, or 0 if not found
     */
    public long getNativeHandle(@NonNull UsbDevice device) {
        SdrSession session = sessionRegistry.getSession(device);
        return session != null ? session.getDevice().getNativeHandle() : 0L;
    }

    /**
     * Gets the native C pointer/handle for a specific SDR device instance.
     * @param sdr the SDR device instance
     * @return native handle, or 0 if none
     */
    public long getNativeHandle(@NonNull SdrDevice sdr) {
        return sdr.getNativeHandle();
    }

    /**
     * Internal callback handling USB device connection events.
     * Initiates the SDR driver creation and session registration.
     */
    private void handleUsbConnected(@NonNull UsbDevice device, @NonNull UsbDeviceConnection connection) {
        if (isClosed.get()) {
            return;
        }
        final long generation = lifecycleGeneration.get();
        if (sessionRegistry.containsDevice(device)) {
            Log.d(TAG, "Device already connected: " + device.getDeviceName());
            return;
        }

        setState(SdrState.INITIALIZING);

        SdrDriver driver = findDriver(device);
        if (driver == null) {
            setError(SdrErrorType.NO_DRIVER,
                    "No registered SDR driver supports USB device: " + device.getDeviceName(), null);
            usbController.disconnect();
            return;
        }

        SdrDevice createdSdr = null;
        try {
            createdSdr = driver.create(device, connection);
            if (createdSdr == null || !createdSdr.isConnected()) {
                if (createdSdr != null) {
                    safeClose(createdSdr);
                }
                setError(SdrErrorType.DRIVER_INITIALIZATION_FAILED,
                        "SDR driver failed to create connected device: " + device.getDeviceName(), null);
                usbController.disconnect();
                return;
            }

            if (isClosed.get() || generation != lifecycleGeneration.get()) {
                safeClose(createdSdr);
                usbController.disconnect();
                return;
            }

            SdrDeviceInfo createdDeviceInfo = getDeviceInfoSafely(createdSdr);
            SdrRole initialRole = createdDeviceInfo != null ? createdDeviceInfo.getRole() : SdrRole.UNASSIGNED;

            SdrSession session = new SdrSession(device, driver, createdSdr, createdDeviceInfo, initialRole);
            synchronized (sdrLock) {
                sessionRegistry.addSession(session);
            }

            setState(SdrState.CONNECTED);
            notifySdrConnected(createdSdr, device);

            Log.i(TAG, "SDR connected: " + device.getDeviceName() + " using " + getDriverName(driver) + " [Role: "
                    + initialRole + "]");
        } catch (Exception e) {
            if (createdSdr != null) {
                safeClose(createdSdr);
            }
            setError(SdrErrorType.DRIVER_INITIALIZATION_FAILED,
                    "Failed to initialize SDR device: " + device.getDeviceName(), e);
            usbController.disconnect();
        }
    }

    /**
     * Internal callback handling USB device disconnection events.
     * Cleans up the active session and notifies listeners.
     */
    private void handleUsbDisconnected(@NonNull UsbDevice device) {
        final SdrSession session = sessionRegistry.removeSession(device);
        final SdrDeviceInfo deviceInfo = session != null ? session.getDeviceInfo() : null;
        final SdrDevice sdrToClose = session != null ? session.getDevice() : null;
        final boolean hadSession = session != null;

        if (sdrToClose != null) {
            synchronized (sdrLock) {
                safeClose(sdrToClose);
            }
        }

        if (sessionRegistry.isEmpty()) {
            if (!isErrorState()) {
                setState(SdrState.DISCONNECTED);
            }
        }

        if (hadSession) {
            notifySdrDisconnected(device, deviceInfo);
        }

        Log.i(TAG, "SDR disconnected: " + device.getDeviceName());
    }

    /**
     * Internal callback mapping underlying USB state changes to SDR lifecycle
     * states.
     */
    private void handleUsbStateChanged(@NonNull UsbConnectionState usbState) {
        if (isClosed.get())
            return;
        switch (usbState.getState()) {
            case CONNECTING:
            case RECONNECTING:
                if (getState() != SdrState.INITIALIZING && getState() != SdrState.CONNECTED) {
                    setState(SdrState.CONNECTING);
                }
                break;
            case DISCONNECTING:
                if (!isErrorState()) {
                    setState(SdrState.DISCONNECTING);
                }
                break;
            case DISCONNECTED:
                if (sessionRegistry.isEmpty()) {
                    sessionRegistry.closeAll(sdrLock);
                    if (!isErrorState()) {
                        setState(SdrState.DISCONNECTED);
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * Internal callback mapping USB connection errors to SDR manager errors.
     */
    private void handleUsbError(@NonNull UsbConnectionState usbState) {
        String message = usbState.getErrorMessage();
        if (message == null || message.isEmpty()) {
            message = "USB connection error: " + usbState.getErrorType().name();
        }
        setError(SdrErrorType.USB_ERROR, message, usbState.getErrorCause());
    }

    /**
     * Safely closes an SDR device without throwing exceptions.
     */
    private void safeClose(@NonNull SdrDevice sdr) {
        try {
            sdr.close();
        } catch (RuntimeException e) {
            Log.e(TAG, "Error closing SDR", e);
        }
    }

    /**
     * Safely reads device info from an SDR without throwing exceptions.
     */
    @Nullable
    private SdrDeviceInfo getDeviceInfoSafely(@NonNull SdrDevice sdr) {
        try {
            return sdr.getDeviceInfo();
        } catch (RuntimeException e) {
            Log.e(TAG, "Error reading SDR device information", e);
            return null;
        }
    }

    /**
     * Updates the manager's overall state.
     */
    private void setState(@NonNull SdrState newState) {
        this.state = newState;
    }

    /**
     * Enters an error state and dispatches the error to listeners.
     */
    private void setError(@NonNull SdrErrorType type, @NonNull String message, @Nullable Throwable cause) {
        setState(SdrState.ERROR);
        Log.e(TAG, message, cause);
        eventNotifier.notifyError(type, message, isClosed);
    }

    /**
     * Clears the current error state and restores the state according to active sessions.
     */
    public void clearError() {
        if (isClosed.get())
            return;
        executeManagerTask(() -> {
            if (isErrorState()) {
                setState(sessionRegistry.isEmpty() ? SdrState.DISCONNECTED : SdrState.CONNECTED);
            }
        });
    }

    /**
     * Checks if the manager is currently in an error state.
     * @return {@code true} if state is {@link SdrState#ERROR}, {@code false} otherwise
     */
    private boolean isErrorState() {
        return state == SdrState.ERROR;
    }

    /**
     * Dispatches an SDR device connected event to registered listeners.
     */
    private void notifySdrConnected(@NonNull SdrDevice sdr, @NonNull UsbDevice device) {
        eventNotifier.notifySdrConnected(sdr, device, sdrLock, isClosed);
    }

    /**
     * Dispatches an SDR device disconnected event to registered listeners.
     */
    private void notifySdrDisconnected(@NonNull UsbDevice device, @Nullable SdrDeviceInfo deviceInfo) {
        eventNotifier.notifySdrDisconnected(device, deviceInfo, isClosed);
    }

    /**
     * Dispatches tasks to the single-threaded manager executor to guarantee
     * sequential processing.
     */
    private void executeManagerTask(@NonNull Runnable task) {
        if (isClosed.get())
            return;
        try {
            managerExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            if (!isClosed.get()) {
                Log.e(TAG, "SDR manager executor rejected a task", e);
            }
        }
    }

    /**
     * Verifies that drivers and device specifications are present before attempting connection.
     * @throws IllegalStateException if no driver or device spec is registered, or if manager is closed
     */
    private void checkReadyForConnection() {
        checkNotClosed();
        if (drivers.isEmpty()) {
            throw new IllegalStateException("At least one SDR driver must be registered before connecting");
        }
        if (usbController.getDeviceSpecs().isEmpty()) {
            throw new IllegalStateException("At least one device specification must be configured before connecting");
        }
    }

    /**
     * Verifies that the manager has not been closed.
     * @throws IllegalStateException if the manager is closed
     */
    private void checkNotClosed() {
        if (isClosed.get()) {
            throw new IllegalStateException("SdrManager has already been closed");
        }
    }

    /**
     * Finds the first registered SDR driver that supports the given USB device.
     * @param device the USB device to match against
     * @return matching {@link SdrDriver}, or {@code null} if none match
     */
    @Nullable
    private SdrDriver findDriver(@NonNull UsbDevice device) {
        for (SdrDriver driver : drivers) {
            try {
                if (driver.supports(device)) {
                    return driver;
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "SDR driver failed while checking device support: " + getDriverName(driver), e);
            }
        }
        return null;
    }

    /**
     * Returns a human-readable display name for the given SDR driver.
     */
    @NonNull
    private static String getDriverName(@NonNull SdrDriver driver) {
        String name = driver.getClass().getSimpleName();
        return name.isEmpty() ? driver.getClass().getName() : name;
    }

    /**
     * Closes the manager, terminating all active SDR sessions and cleaning up
     * executors.
     * The manager cannot be reused after closing.
     */
    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        lifecycleGeneration.incrementAndGet();
        usbController.removeListener(usbListener);
        sessionRegistry.closeAll(sdrLock);
        usbController.cleanup();
        setState(SdrState.CLOSED);
        eventNotifier.clearListeners();
        drivers.clear();
        managerExecutor.shutdown();
        Log.d(TAG, "SdrManager closed");
    }
}