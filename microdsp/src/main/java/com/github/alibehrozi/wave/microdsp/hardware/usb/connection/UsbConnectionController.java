package com.github.alibehrozi.wave.microdsp.hardware.usb.connection;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.alibehrozi.wave.microdsp.hardware.usb.core.UsbDeviceSpec;
import com.github.alibehrozi.wave.microdsp.hardware.usb.permission.UsbPermissionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controls the USB connection lifecycle.
 *
 * <p>
 * This class coordinates USB device discovery, permission requests,
 * hotplug events, connection management, auto-connect functionality,
 * and connection state updates.
 * </p>
 *
 * <p>
 * The controller owns the active {@link UsbDeviceConnection}.
 * Callers must not close the connection directly. Use
 * {@link #disconnect()} or {@link #cleanup()} to release the connection.
 * </p>
 */
public final class UsbConnectionController {

    private static final String TAG = "UsbConnectionController";

    /*
     * Error codes used by the connection controller.
     */
    private static final int ERROR_DEVICE_NOT_FOUND = -1001;
    private static final int ERROR_PERMISSION_DENIED = -1002;
    private static final int ERROR_OPEN_FAILED = -1003;
    private static final int ERROR_CONNECTION_ERROR = -1005;

    private final UsbConnectionManager connectionManager;
    private final UsbPermissionManager permissionManager;
    private final UsbHotplugManager hotplugManager;

    /*
     * All connection operations are serialized through this executor.
     *
     * This prevents simultaneous connect, disconnect, permission-result,
     * and hotplug operations from modifying the connection state at
     * the same time.
     */
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();

    /*
     * Listener callbacks are delivered on the main thread.
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /*
     * Connection listeners.
     */
    private final CopyOnWriteArrayList<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    /*
     * Current connection state.
     *
     * This object is only modified by connectionExecutor.
     * Public callers receive copies through getConnectionState().
     */
    private final UsbConnectionState connectionState = new UsbConnectionState();

    /*
     * Prevents operations after cleanup.
     */
    private final AtomicBoolean isCleanedUp = new AtomicBoolean(false);

    /*
     * Identifies the latest connection operation.
     *
     * Whenever a new connect or disconnect operation begins,
     * the generation is incremented. Older asynchronous callbacks
     * are ignored if their generation no longer matches.
     */
    private final AtomicLong operationGeneration = new AtomicLong(0);

    /*
     * Current USB device and connection.
     *
     * These references are volatile because they can be read by callers
     * outside the connection executor.
     */
    @Nullable
    private volatile UsbDevice currentDevice;

    @Nullable
    private volatile UsbDeviceConnection currentConnection;

    /*
     * Device specifications used by auto-connect.
     */
    private final CopyOnWriteArrayList<UsbDeviceSpec> deviceSpecs = new CopyOnWriteArrayList<>();

    /*
     * Auto-connect state.
     */
    private final AtomicBoolean autoConnectEnabled = new AtomicBoolean(false);

    /*
     * Device currently waiting for an Android permission result.
     */
    @Nullable
    private volatile String pendingPermissionDeviceName;

    /*
     * Hotplug listener owned by this controller.
     */
    private final UsbHotplugManager.HotplugListener hotplugListener = new UsbHotplugManager.HotplugListener() {

        @Override
        public void onDeviceAttached(@NonNull UsbDevice device) {
            handleDeviceAttached(device);
        }

        @Override
        public void onDeviceDetached(@NonNull UsbDevice device) {
            handleDeviceDetached(device);
        }
    };

    /**
     * Listener for USB connection lifecycle events.
     *
     * <p>
     * All listener callbacks are delivered on the main thread.
     * </p>
     */
    public interface ConnectionListener {

        /**
         * Called when a USB device has been successfully opened.
         *
         * <p>
         * The returned connection is owned by
         * {@link UsbConnectionController}. The listener must not call
         * {@link UsbDeviceConnection#close()} directly.
         * </p>
         * @param device     connected USB device
         * @param connection opened USB connection
         */
        void onDeviceConnected(@NonNull UsbDevice device, @NonNull UsbDeviceConnection connection);

        /**
         * Called when the active USB device is disconnected.
         * @param device disconnected USB device
         */
        void onDeviceDisconnected(@NonNull UsbDevice device);

        /**
         * Called whenever the connection state changes.
         * @param state snapshot of the new connection state
         */
        default void onStateChanged(@NonNull UsbConnectionState state) {
            // Optional - can be overridden
        }

        /**
         * Called when a connection error occurs.
         * @param state snapshot containing error information
         */
        default void onError(@NonNull UsbConnectionState state) {
            // Optional - can be overridden
        }
    }

    /**
     * Creates a new USB connection controller.
     * @param context Android context
     */
    public UsbConnectionController(@NonNull android.content.Context context) {

        this(
                new UsbConnectionManager(context),
                new UsbPermissionManager(context),
                new UsbHotplugManager(context));
    }

    /**
     * Creates a USB connection controller using the supplied components.
     *
     * <p>
     * This constructor is useful when the USB components need to be
     * provided externally, such as during testing.
     * </p>
     * @param connectionManager USB connection manager
     * @param permissionManager USB permission manager
     * @param hotplugManager    USB hotplug manager
     */
    public UsbConnectionController(
            @NonNull UsbConnectionManager connectionManager,
            @NonNull UsbPermissionManager permissionManager,
            @NonNull UsbHotplugManager hotplugManager) {

        this.connectionManager = connectionManager;
        this.permissionManager = permissionManager;
        this.hotplugManager = hotplugManager;

        /*
         * Listen for USB attach and detach events.
         */
        this.hotplugManager.addListener(hotplugListener);

        /*
         * Initialize the connection state.
         */
        connectionState.setAutoConnect(false);

        Log.d(TAG, "UsbConnectionController created");
    }

    /**
     * Sets the USB device specifications used by auto-connect.
     *
     * <p>
     * If auto-connect is currently enabled, the controller will
     * automatically re-evaluate the currently connected USB devices.
     * </p>
     * @param specs USB device specifications
     */
    public void setDeviceSpecs(@NonNull List<UsbDeviceSpec> specs) {

        checkNotCleanedUp();

        deviceSpecs.clear();
        deviceSpecs.addAll(specs);

        Log.d(TAG, "Device specifications set: " + specs);

        /*
         * If auto-connect is enabled, re-evaluate the connection.
         */
        if (autoConnectEnabled.get()) {
            long operationId = operationGeneration.incrementAndGet();
            connectionExecutor.execute(() -> handleDeviceSpecsChanged(operationId));
        }
    }

    /**
     * Sets a single USB device specification for auto-connect.
     *
     * <p>
     * This replaces all currently configured specifications.
     * </p>
     * @param spec USB device specification
     */
    public void setDeviceSpec(@NonNull UsbDeviceSpec spec) {

        checkNotCleanedUp();

        deviceSpecs.clear();
        deviceSpecs.add(spec);

        Log.d(TAG, "Device specification set: " + spec);

        /*
         * If auto-connect is enabled, re-evaluate the connection.
         */
        if (autoConnectEnabled.get()) {
            long operationId = operationGeneration.incrementAndGet();
            connectionExecutor.execute(() -> handleDeviceSpecsChanged(operationId));
        }
    }

    /**
     * Adds a USB device specification for auto-connect.
     * @param spec USB device specification to add
     */
    public void addDeviceSpec(@NonNull UsbDeviceSpec spec) {

        checkNotCleanedUp();

        if (!deviceSpecs.contains(spec)) {
            deviceSpecs.add(spec);

            Log.d(TAG, "Device specification added: " + spec);

            if (autoConnectEnabled.get()) {
                long operationId = operationGeneration.incrementAndGet();
                connectionExecutor.execute(() -> checkCurrentlyAttachedDevices(operationId));
            }
        }
    }

    /**
     * Removes a USB device specification for auto-connect.
     * @param spec USB device specification to remove
     */
    public void removeDeviceSpec(@NonNull UsbDeviceSpec spec) {

        checkNotCleanedUp();

        if (deviceSpecs.remove(spec)) {
            Log.d(TAG, "Device specification removed: " + spec);

            if (autoConnectEnabled.get() && currentDevice != null
                    && spec.matches(currentDevice.getVendorId(), currentDevice.getProductId())) {
                disconnect();
            }
        }
    }

    /**
     * Gets the USB device specifications currently used by auto-connect.
     * @return configured USB device specifications
     */
    @NonNull
    public List<UsbDeviceSpec> getDeviceSpecs() {
        return new CopyOnWriteArrayList<>(deviceSpecs);
    }

    /**
     * Gets the first configured USB device specification.
     * @return configured USB device specification, or {@code null}
     */
    @Nullable
    public UsbDeviceSpec getDeviceSpec() {
        return deviceSpecs.isEmpty() ? null : deviceSpecs.get(0);
    }

    /**
     * Returns all currently attached USB devices that match any of the
     * configured device specifications.
     * @return list of matching USB devices
     */
    @NonNull
    public List<UsbDevice> getAvailableDevices() {
        if (isCleanedUp.get()) {
            return Collections.emptyList();
        }

        List<UsbDevice> matched = new ArrayList<>();
        List<UsbDevice> allDevices = connectionManager.getConnectedDevices();

        for (UsbDevice device : allDevices) {
            for (UsbDeviceSpec spec : deviceSpecs) {
                if (spec.matches(device.getVendorId(), device.getProductId())) {
                    matched.add(device);
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * Adds a connection listener.
     * @param listener listener to add
     */
    public void addListener(@NonNull ConnectionListener listener) {

        if (isCleanedUp.get()) {
            Log.w(TAG, "Cannot add listener after cleanup");
            return;
        }

        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "Listener added, total: " + listeners.size());
        }
    }

    /**
     * Removes a connection listener.
     * @param listener listener to remove
     */
    public void removeListener(@NonNull ConnectionListener listener) {
        listeners.remove(listener);
        Log.d(TAG, "Listener removed, total: " + listeners.size());
    }

    /**
     * Clears all connection listeners.
     */
    public void clearListeners() {

        listeners.clear();
        Log.d(TAG, "All connection listeners cleared");
    }

    /**
     * Enables or disables automatic USB connection handling.
     *
     * <p>
     * When enabled, the controller:
     * starts USB hotplug monitoring,
     * checks for already connected devices,
     * and automatically connects to the first matching device.
     * </p>
     *
     * <p>
     * A device specification must be configured using
     * {@link #setDeviceSpec(UsbDeviceSpec)} before enabling auto-connect.
     * </p>
     *
     * <p>
     * Disabling auto-connect does not disconnect an already connected
     * device. It only stops automatic connection handling.
     * </p>
     * @param enabled {@code true} to enable auto-connect
     */
    public void setAutoConnectEnabled(boolean enabled) {

        checkNotCleanedUp();

        if (enabled == autoConnectEnabled.get()) {
            return;
        }

        if (enabled) {

            if (deviceSpecs.isEmpty()) {

                throw new IllegalStateException(
                        "At least one device specification must be set before " +
                                "enabling auto-connect");
            }

            autoConnectEnabled.set(true);
            connectionState.setAutoConnect(true);

            /*
             * Start listening for future USB attach and detach events.
             */
            hotplugManager.start();
            notifyStateChanged();

            Log.i(TAG, "Auto-connect enabled");

            /*
             * Check for devices that were already connected before
             * auto-connect was enabled.
             */
            long operationId = operationGeneration.incrementAndGet();
            connectionExecutor.execute(() -> checkCurrentlyAttachedDevices(operationId));

        } else {
            autoConnectEnabled.set(false);

            /*
             * Invalidate pending auto-connect operations.
             */
            operationGeneration.incrementAndGet();
            pendingPermissionDeviceName = null;
            connectionState.setAutoConnect(false);

            /*
             * Stop monitoring future hotplug events.
             *
             * The currently active connection, if any, remains active.
             */
            hotplugManager.stop();

            /*
             * If we are currently waiting for permission, there is no
             * active connection yet. Return to DISCONNECTED.
             */
            connectionExecutor.execute(this::cancelPendingAutoConnect);

            notifyStateChanged();
            Log.i(TAG, "Auto-connect disabled");
        }
    }

    /**
     * Checks whether auto-connect is currently enabled.
     * @return {@code true} if auto-connect is enabled
     */
    public boolean isAutoConnectEnabled() {
        return autoConnectEnabled.get();
    }

    /**
     * Connects to the first currently connected USB device matching
     * any of the configured device specifications.
     *
     * <p>
     * The operation is asynchronous.
     * </p>
     *
     * <p>
     * If no device specifications have been configured, the connection
     * fails with a connection error.
     * </p>
     */
    public void connect() {
        checkNotCleanedUp();

        long operationId = operationGeneration.incrementAndGet();
        connectionExecutor.execute(() -> connectUsingConfiguredSpecs(operationId));
    }

    /**
     * Connects to the first currently connected USB device matching
     * the specified device specification.
     *
     * <p>
     * The supplied specification is added to the current device
     * specifications used by future auto-connect operations.
     * </p>
     * @param spec USB device specification
     */
    public void connect(@NonNull UsbDeviceSpec spec) {
        checkNotCleanedUp();

        if (!deviceSpecs.contains(spec)) {
            deviceSpecs.add(spec);
        }

        long operationId = operationGeneration.incrementAndGet();
        connectionExecutor.execute(() -> connectUsingSpec(spec, operationId, false, false));
    }

    /**
     * Connects directly to the specified USB device.
     *
     * <p>
     * The operation is asynchronous.
     * </p>
     *
     * <p>
     * If permission has not already been granted, the controller
     * requests permission and continues the connection after Android
     * returns the permission result.
     * </p>
     * @param device USB device to connect
     */
    public void connect(@NonNull UsbDevice device) {
        checkNotCleanedUp();

        long operationId = operationGeneration.incrementAndGet();
        connectionExecutor.execute(() -> connectToDevice(device, operationId, false));
    }

    /**
     * Attempts to reconnect to the configured USB device.
     *
     * <p>
     * If a current device is available, that device is preferred.
     * Otherwise the configured device specification is used.
     * </p>
     */
    public void reconnect() {

        checkNotCleanedUp();

        long operationId = operationGeneration.incrementAndGet();

        connectionExecutor.execute(() -> {
            if (currentDevice != null) {
                connectToDevice(currentDevice, operationId, true);
                return;
            }

            if (!deviceSpecs.isEmpty()) {
                connectUsingSpecs(deviceSpecs, operationId, false, true);
                return;
            }

            setError(UsbConnectionState.ErrorType.DEVICE_NOT_FOUND, ERROR_DEVICE_NOT_FOUND,
                    "No USB device or device specification is available for reconnect");
        });
    }

    /**
     * Disconnects the current USB device.
     *
     * <p>
     * The operation is asynchronous.
     * </p>
     *
     * <p>
     * Disconnecting does not disable auto-connect.
     * If auto-connect remains enabled, a future attach event can
     * establish a new connection.
     * </p>
     */
    public void disconnect() {

        if (isCleanedUp.get()) {
            return;
        }

        long operationId = operationGeneration.incrementAndGet();

        // Note: pendingPermissionDeviceName is only mutated from within
        // the connectionExecutor. Clearing it here (outside the executor)
        // was a data race. It is now cleared inside the executor task below.
        connectionExecutor.execute(() -> {
            pendingPermissionDeviceName = null;
            disconnectCurrentDevice(operationId, true);
        });
    }

    /**
     * Gets the current connection state.
     *
     * <p>
     * The returned state is a snapshot and can be safely used
     * by the caller.
     * </p>
     * @return current connection state
     */
    @NonNull
    public UsbConnectionState getConnectionState() {
        return connectionState.copy();
    }

    /**
     * Gets the currently connected USB device.
     * @return current USB device, or {@code null}
     */
    @Nullable
    public UsbDevice getCurrentDevice() {
        return currentDevice;
    }

    /**
     * Gets the current USB device connection.
     *
     * <p>
     * The returned connection is owned by this controller.
     * Callers must not close it directly.
     * </p>
     *
     * <p>
     * Use {@link #disconnect()} to close the active connection.
     * </p>
     * @return current USB device connection, or {@code null}
     */
    @Nullable
    public UsbDeviceConnection getCurrentConnection() {
        return currentConnection;
    }

    /**
     * Checks whether a USB device is currently connected.
     * @return {@code true} if an active USB connection exists
     */
    public boolean isConnected() {
        return currentConnection != null;
    }

    /**
     * Clears the current connection error information.
     *
     * <p>
     * This method does not automatically change the current
     * connection state.
     * </p>
     */
    public void clearError() {

        if (isCleanedUp.get()) {
            return;
        }

        connectionExecutor.execute(() -> {
            connectionState.clearError();
            notifyStateChanged();
        });
    }

    /**
     * Resets the connection state.
     *
     * <p>
     * If a connection is active, it is closed first.
     * </p>
     */
    public void resetConnectionState() {

        if (isCleanedUp.get()) {
            return;
        }

        long operationId = operationGeneration.incrementAndGet();
        pendingPermissionDeviceName = null;
        connectionExecutor.execute(() -> {

            closeCurrentConnection();

            currentDevice = null;
            currentConnection = null;
            connectionState.reset();
            connectionState.setAutoConnect(autoConnectEnabled.get());

            notifyStateChanged();

            if (autoConnectEnabled.get()) {
                checkCurrentlyAttachedDevices(operationId);
            }
        });
    }

    /**
     * Handles a USB device attachment event.
     * @param device attached USB device
     */
    private void handleDeviceAttached(@NonNull UsbDevice device) {

        if (isCleanedUp.get() || !autoConnectEnabled.get()) {
            return;
        }

        /*
         * Hotplug callbacks may come from the hotplug manager's
         * background thread. Serialize the connection logic here.
         */
        connectionExecutor.execute(() -> {

            if (isCleanedUp.get() || !autoConnectEnabled.get()) {
                return;
            }

            boolean matched = false;
            for (UsbDeviceSpec spec : deviceSpecs) {
                if (spec.matches(device.getVendorId(), device.getProductId())) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                return;
            }

            /*
             * Do not replace an already active connection.
             */
            if (currentConnection != null) {
                return;
            }

            /*
             * Ignore duplicate attach events while the same device
             * is already being connected.
             */
            if (currentDevice != null && currentDevice.getDeviceName().equals(device.getDeviceName())
                    && connectionState.isConnecting()) {
                return;
            }

            long operationId = operationGeneration.incrementAndGet();
            connectToDevice(device, operationId, false);
        });
    }

    /**
     * Handles a USB device detachment event.
     * @param device detached USB device
     */
    private void handleDeviceDetached(@NonNull UsbDevice device) {

        if (isCleanedUp.get()) {
            return;
        }

        connectionExecutor.execute(() -> {
            String deviceName = device.getDeviceName();

            /*
             * Ignore detach events for devices that are unrelated
             * to the current connection.
             */
            if (currentDevice == null || !deviceName.equals(currentDevice.getDeviceName())) {

                /*
                 * A permission dialog may still be pending for this
                 * device. Invalidate that pending operation.
                 */
                if (deviceName.equals(pendingPermissionDeviceName)) {
                    operationGeneration.incrementAndGet();
                    permissionManager.cancelPermissionRequest(device);
                    pendingPermissionDeviceName = null;
                    currentDevice = null;
                    connectionState.setDevice(null);
                    connectionState.clearError();
                    connectionState.setState(UsbConnectionState.State.DISCONNECTED);

                    notifyStateChanged();
                }

                return;
            }

            /*
             * Invalidate any pending permission or connection callback.
             */
            long operationId = operationGeneration.incrementAndGet();

            pendingPermissionDeviceName = null;

            /*
             * The device was connected successfully.
             * Close the active connection and notify listeners.
             */
            disconnectCurrentDevice(operationId, true);
        });
    }

    /**
     * Checks for USB devices that were already attached before
     * auto-connect was enabled.
     * @param operationId current operation ID
     */
    private void checkCurrentlyAttachedDevices(long operationId) {
        if (!isCurrentOperation(operationId) || !autoConnectEnabled.get()) {
            return;
        }

        if (deviceSpecs.isEmpty()) {
            return;
        }

        /*
         * Do not replace an active connection.
         */
        if (currentConnection != null) {
            return;
        }

        UsbDevice device = null;
        for (UsbDeviceSpec spec : deviceSpecs) {
            device = connectionManager.findFirstDevice(spec);
            if (device != null) {
                break;
            }
        }

        if (device == null) {
            Log.d(TAG, "No matching USB device currently attached");
            return;
        }

        connectToDevice(device, operationId, false);
    }

    /**
     * Connects using the currently configured device specifications.
     * @param operationId current operation ID
     */
    private void connectUsingConfiguredSpecs(long operationId) {
        if (deviceSpecs.isEmpty()) {

            setError(UsbConnectionState.ErrorType.DEVICE_NOT_FOUND, ERROR_DEVICE_NOT_FOUND,
                    "No USB device specifications have been configured");

            return;
        }

        connectUsingSpecs(deviceSpecs, operationId, false, false);
    }

    /**
     * Finds and connects to the first device matching any of the specifications.
     * @param specs              USB device specifications
     * @param operationId        current operation ID
     * @param autoConnectAttempt whether this is an auto-connect attempt
     * @param reconnecting       whether this is a reconnect operation
     */
    private void connectUsingSpecs(@NonNull List<UsbDeviceSpec> specs, long operationId,
                                   boolean autoConnectAttempt, boolean reconnecting) {

        if (!isCurrentOperation(operationId)) {
            return;
        }

        if (autoConnectAttempt && !autoConnectEnabled.get()) {
            return;
        }

        /*
         * Do not replace an already active connection during
         * auto-connect operations.
         */
        if (autoConnectAttempt && currentConnection != null) {
            return;
        }

        UsbDevice device = null;
        for (UsbDeviceSpec spec : specs) {
            device = connectionManager.findFirstDevice(spec);
            if (device != null) {
                break;
            }
        }

        if (device == null) {
            if (autoConnectAttempt) {
                return;
            }

            setError(UsbConnectionState.ErrorType.DEVICE_NOT_FOUND, ERROR_DEVICE_NOT_FOUND,
                    "No connected USB device matches configured specifications");

            return;
        }

        connectToDevice(device, operationId, reconnecting);
    }

    /**
     * Finds and connects to the first device matching a specification.
     * @param spec               USB device specification
     * @param operationId        current operation ID
     * @param autoConnectAttempt whether this is an auto-connect attempt
     * @param reconnecting       whether this is a reconnect operation
     */
    private void connectUsingSpec(@NonNull UsbDeviceSpec spec, long operationId,
                                  boolean autoConnectAttempt, boolean reconnecting) {

        if (!isCurrentOperation(operationId)) {
            return;
        }

        if (autoConnectAttempt && !autoConnectEnabled.get()) {
            return;
        }

        /*
         * Do not replace an already active connection during
         * auto-connect operations.
         */
        if (autoConnectAttempt && currentConnection != null) {
            return;
        }

        UsbDevice device = connectionManager.findFirstDevice(spec);
        if (device == null) {

            if (autoConnectAttempt) {

                /*
                 * No matching device is normal during auto-connect.
                 * Remain disconnected without reporting an error.
                 */
                return;
            }

            setError(UsbConnectionState.ErrorType.DEVICE_NOT_FOUND, ERROR_DEVICE_NOT_FOUND,
                    "No connected USB device matches: " + spec);

            return;
        }

        connectToDevice(device, operationId, reconnecting);
    }

    /**
     * Handles a change to the auto-connect device specifications.
     * @param operationId current operation ID
     */
    private void handleDeviceSpecsChanged(long operationId) {

        if (!isCurrentOperation(operationId) || !autoConnectEnabled.get()) {
            return;
        }

        /*
         * If the current connection no longer matches any of the new
         * device specifications, disconnect it first.
         */
        if (currentDevice != null) {
            boolean matched = false;
            for (UsbDeviceSpec spec : deviceSpecs) {
                if (spec.matches(currentDevice.getVendorId(), currentDevice.getProductId())) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                disconnectCurrentDevice(operationId, true);
            }
        }

        if (!isCurrentOperation(operationId)) {
            return;
        }

        /*
         * Check for a device matching the new specifications.
         */
        checkCurrentlyAttachedDevices(operationId);
    }

    /**
     * Connects to a specific USB device.
     * @param device       USB device to connect
     * @param operationId  current operation ID
     * @param reconnecting whether this is a reconnect operation
     */
    private void connectToDevice(@NonNull UsbDevice device, long operationId, boolean reconnecting) {

        if (!isCurrentOperation(operationId)) {
            return;
        }

        /*
         * Do not connect if another connection is already active
         * for the same device.
         */
        if (currentConnection != null && currentDevice != null
                && currentDevice.getDeviceName().equals(device.getDeviceName())) {
            return;
        }

        /*
         * Manual connections replace an existing connection.
         */
        if (currentConnection != null) {
            disconnectCurrentDevice(operationId, true);
            if (!isCurrentOperation(operationId)) {
                return;
            }
        }

        /*
         * If a permission request for the same device is already
         * in progress, wait for that request to finish.
         */
        if (device.getDeviceName().equals(pendingPermissionDeviceName)) {
            return;
        }

        /*
         * Store the device as the current connection target.
         */
        currentDevice = device;

        connectionState.setDevice(device);
        connectionState.clearError();

        /*
         * Update the state before beginning the connection process.
         */
        if (reconnecting) {
            connectionState.setState(UsbConnectionState.State.RECONNECTING);
            connectionState.incrementReconnectionAttempts();

        } else {
            connectionState.setState(UsbConnectionState.State.CONNECTING);
            connectionState.incrementConnectionAttempts();
        }

        notifyStateChanged();

        /*
         * Verify that the device is still physically connected.
         */
        if (!connectionManager.isDeviceAvailable(device)) {
            setError(UsbConnectionState.ErrorType.DEVICE_NOT_FOUND, ERROR_DEVICE_NOT_FOUND,
                    "USB device is no longer available: " + device.getDeviceName());
            return;
        }

        /*
         * Check USB permission before opening the device.
         */
        if (!permissionManager.hasPermission(device)) {
            requestPermission(device, operationId, reconnecting);
            return;
        }

        /*
         * Permission is already granted.
         * Open the device immediately.
         */
        openDevice(device, operationId);
    }

    /**
     * Requests USB permission for a device.
     * @param device       USB device
     * @param operationId  current operation ID
     * @param reconnecting whether this is a reconnect operation
     */
    private void requestPermission(
            @NonNull UsbDevice device,
            long operationId,
            boolean reconnecting) {

        if (!isCurrentOperation(operationId)) {
            return;
        }

        pendingPermissionDeviceName = device.getDeviceName();

        /*
         * The permission manager handles the asynchronous Android
         * permission result. The callback is returned on the main
         * thread, so the result is serialized back through the
         * connection executor.
         */
        permissionManager.requestPermission(device,
                new UsbPermissionManager.PermissionCallback() {

                    @Override
                    public void onPermissionGranted(@NonNull UsbDevice grantedDevice) {

                        connectionExecutor.execute(() -> {

                            if (!isCurrentOperation(operationId)) {
                                return;
                            }

                            if (!isCurrentDevice(grantedDevice)) {
                                return;
                            }

                            pendingPermissionDeviceName = null;
                            openDevice(grantedDevice, operationId);
                        });
                    }

                    @Override
                    public void onPermissionDenied(@NonNull UsbDevice deniedDevice) {

                        connectionExecutor.execute(() -> {

                            if (!isCurrentOperation(operationId)) {
                                return;
                            }

                            if (!isCurrentDevice(deniedDevice)) {
                                return;
                            }

                            pendingPermissionDeviceName = null;

                            setError(UsbConnectionState.ErrorType.PERMISSION_DENIED, ERROR_PERMISSION_DENIED,
                                    "USB permission denied for device: " + deniedDevice.getDeviceName());
                        });
                    }

                    @Override
                    public void onPermissionError(
                            @NonNull UsbDevice errorDevice,
                            @NonNull String message,
                            @Nullable Throwable cause) {

                        connectionExecutor.execute(() -> {

                            if (!isCurrentOperation(operationId)) {
                                return;
                            }

                            if (!isCurrentDevice(errorDevice)) {
                                return;
                            }

                            pendingPermissionDeviceName = null;

                            setError(UsbConnectionState.ErrorType.UNKNOWN, ERROR_CONNECTION_ERROR, message, cause);
                        });
                    }
                });

        Log.d(TAG, "Waiting for USB permission: " + device.getDeviceName());
    }

    /**
     * Opens a USB device after permission has been granted.
     * @param device      USB device to open
     * @param operationId current operation ID
     */
    private void openDevice(@NonNull UsbDevice device, long operationId) {

        if (!isCurrentOperation(operationId)) {
            return;
        }

        if (!isCurrentDevice(device)) {
            return;
        }

        pendingPermissionDeviceName = null;

        /*
         * Re-check availability immediately before opening.
         */
        if (!connectionManager.isDeviceAvailable(device)) {
            setError(UsbConnectionState.ErrorType.DEVICE_NOT_FOUND, ERROR_DEVICE_NOT_FOUND,
                    "USB device is no longer available: " + device.getDeviceName());
            return;
        }

        /*
         * Re-check permission immediately before opening.
         */
        if (!permissionManager.hasPermission(device)) {
            setError(UsbConnectionState.ErrorType.PERMISSION_DENIED, ERROR_PERMISSION_DENIED,
                    "USB permission is not available: " + device.getDeviceName());
            return;
        }

        /*
         * Open the USB device.
         */
        UsbDeviceConnection connection = connectionManager.open(device);

        if (connection == null) {
            setError(UsbConnectionState.ErrorType.OPEN_FAILED, ERROR_OPEN_FAILED,
                    "Failed to open USB connection for device: " + device.getDeviceName());
            return;
        }

        /*
         * Make sure this connection attempt is still valid.
         */
        if (!isCurrentOperation(operationId) || !isCurrentDevice(device)) {

            /*
             * The connection is no longer wanted.
             * Close it immediately.
             */
            connectionManager.close(connection);

            return;
        }

        /*
         * Store the active connection.
         */
        currentConnection = connection;
        currentDevice = device;
        connectionState.setDevice(device);
        connectionState.clearError();
        connectionState.setState(UsbConnectionState.State.CONNECTED);

        Log.i(TAG, "USB connection established: " + device.getDeviceName());

        /*
         * Notify state listeners first.
         */
        notifyStateChanged();

        /*
         * Notify connection listeners with the active connection.
         */
        notifyDeviceConnected(device, connection);
    }

    /**
     * Disconnects the current device.
     * @param operationId    current operation ID
     * @param notifyListener whether to notify device-disconnected listeners
     */
    private void disconnectCurrentDevice(long operationId, boolean notifyListener) {

        if (isCleanedUp.get()) {
            return;
        }

        /*
         * Keep a reference to the device before clearing it.
         */
        UsbDevice device = currentDevice;
        UsbDeviceConnection connection = currentConnection;

        if (device != null && pendingPermissionDeviceName != null
                && device.getDeviceName().equals(pendingPermissionDeviceName)) {
            permissionManager.cancelPermissionRequest(device);
        }

        pendingPermissionDeviceName = null;

        /*
         * If there is no active connection, clear any pending
         * connection target and return to DISCONNECTED.
         */
        if (connection == null) {

            currentDevice = null;
            currentConnection = null;

            connectionState.setDevice(null);
            connectionState.clearError();

            if (connectionState.getState() != UsbConnectionState.State.DISCONNECTED) {
                connectionState.setState(UsbConnectionState.State.DISCONNECTED);
                notifyStateChanged();
            }

            return;
        }

        /*
         * Update state before closing the active connection.
         */
        connectionState.setState(UsbConnectionState.State.DISCONNECTING);

        notifyStateChanged();

        /*
         * Close the USB connection.
         */
        connectionManager.close(connection);

        /*
         * Clear active connection references.
         */
        currentConnection = null;
        currentDevice = null;

        connectionState.setDevice(null);
        connectionState.clearError();
        connectionState.setState(UsbConnectionState.State.DISCONNECTED);

        Log.i(TAG, "USB connection closed");

        /*
         * Notify listeners that the device has disconnected.
         */
        notifyStateChanged();

        if (notifyListener && device != null) {
            notifyDeviceDisconnected(device);
        }
    }

    /**
     * Closes the current connection without generating lifecycle callbacks.
     *
     * <p>
     * This method is used during cleanup.
     * </p>
     */
    private void closeCurrentConnection() {
        UsbDeviceConnection connection = currentConnection;

        if (connection != null) {
            connectionManager.close(connection);
        }

        currentConnection = null;
        if (currentDevice != null && pendingPermissionDeviceName != null) {
            permissionManager.cancelPermissionRequest(currentDevice);
        }

        currentDevice = null;
        pendingPermissionDeviceName = null;
    }

    /**
     * Cancels a pending auto-connect operation.
     */
    private void cancelPendingAutoConnect() {

        if (currentConnection != null) {
            return;
        }

        if (!connectionState.isConnecting()) {
            return;
        }

        if (currentDevice != null && pendingPermissionDeviceName != null) {
            permissionManager.cancelPermissionRequest(currentDevice);
        }

        currentDevice = null;
        pendingPermissionDeviceName = null;

        connectionState.setDevice(null);
        connectionState.clearError();
        connectionState.setState(UsbConnectionState.State.DISCONNECTED);

        notifyStateChanged();
    }

    /**
     * Sets an error on the current connection state.
     * @param errorType error type
     * @param errorCode error code
     * @param message   error message
     */
    private void setError(@NonNull UsbConnectionState.ErrorType errorType, int errorCode,
                          @NonNull String message) {
        setError(errorType, errorCode, message, null);
    }

    /**
     * Sets an error on the current connection state.
     * @param errorType error type
     * @param errorCode error code
     * @param message   error message
     * @param cause     optional error cause
     */
    private void setError(@NonNull UsbConnectionState.ErrorType errorType, int errorCode,
                          @NonNull String message, @Nullable Throwable cause) {

        connectionState.setError(errorType, errorCode, message, cause);

        Log.e(TAG, message, cause);
        notifyStateChanged();
        notifyError();
    }

    /**
     * Checks whether the specified device is the current connection target.
     * @param device USB device
     * @return {@code true} if the device is the current target
     */
    private boolean isCurrentDevice(@NonNull UsbDevice device) {

        UsbDevice current = currentDevice;
        return current != null && current.getDeviceName().equals(device.getDeviceName());
    }

    /**
     * Checks whether an operation is still valid.
     * @param operationId operation ID
     * @return {@code true} if the operation is still current
     */
    private boolean isCurrentOperation(long operationId) {
        return !isCleanedUp.get() && operationGeneration.get() == operationId;
    }

    /**
     * Notifies listeners that a USB device connected.
     * @param device     connected USB device
     * @param connection active USB connection
     */
    private void notifyDeviceConnected(@NonNull UsbDevice device, @NonNull UsbDeviceConnection connection) {
        for (ConnectionListener listener : listeners) {
            mainHandler.post(() -> {
                if (isCleanedUp.get())
                    return;
                try {
                    listener.onDeviceConnected(device, connection);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onDeviceConnected callback", e);
                }
            });
        }
    }

    /**
     * Notifies listeners that a USB device disconnected.
     * @param device disconnected USB device
     */
    private void notifyDeviceDisconnected(@NonNull UsbDevice device) {
        for (ConnectionListener listener : listeners) {
            mainHandler.post(() -> {
                if (isCleanedUp.get())
                    return;
                try {
                    listener.onDeviceDisconnected(device);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onDeviceDisconnected callback", e);
                }
            });
        }
    }

    /**
     * Notifies listeners that the connection state changed.
     */
    private void notifyStateChanged() {
        UsbConnectionState snapshot = connectionState.copy();
        for (ConnectionListener listener : listeners) {
            mainHandler.post(() -> {
                if (isCleanedUp.get())
                    return;
                try {
                    listener.onStateChanged(snapshot.copy());
                } catch (Exception e) {
                    Log.e(TAG, "Error in onStateChanged callback", e);
                }
            });
        }
    }

    /**
     * Notifies listeners that a connection error occurred.
     */
    private void notifyError() {
        UsbConnectionState snapshot = connectionState.copy();
        for (ConnectionListener listener : listeners) {
            mainHandler.post(() -> {
                if (isCleanedUp.get())
                    return;
                try {
                    listener.onError(snapshot.copy());
                } catch (Exception e) {
                    Log.e(TAG, "Error in onError callback", e);
                }
            });
        }
    }

    /**
     * Verifies that the controller has not been cleaned up.
     */
    private void checkNotCleanedUp() {
        if (isCleanedUp.get()) {
            throw new IllegalStateException("UsbConnectionController has already been cleaned up");
        }
    }

    /**
     * Cleans up resources and disconnects the current USB device.
     *
     * <p>
     * After cleanup, the controller cannot be used again.
     * </p>
     */
    public void cleanup() {

        /*
         * Cleanup can only happen once.
         */
        if (!isCleanedUp.compareAndSet(false, true)) {
            return;
        }

        /*
         * Invalidate all pending connection and permission callbacks.
         */
        operationGeneration.incrementAndGet();
        autoConnectEnabled.set(false);
        pendingPermissionDeviceName = null;

        /*
         * Stop receiving hotplug events immediately.
         */
        hotplugManager.removeListener(hotplugListener);
        hotplugManager.stop();

        /*
         * Permission requests can no longer be used by this controller.
         */
        permissionManager.cleanup();

        /*
         * Queue final connection cleanup on the same executor that
         * owns all connection state changes.
         */
        connectionExecutor.execute(() -> {

            /*
             * Close any active USB connection.
             */
            closeCurrentConnection();

            /*
             * Reset the internal state without notifying external
             * listeners. Cleanup is the end of the lifecycle.
             */
            connectionState.reset();
            connectionState.setAutoConnect(false);

            /*
             * Remove all listeners.
             */
            listeners.clear();

            /*
             * Stop accepting new connection operations.
             */
            connectionExecutor.shutdown();

            Log.d(TAG, "UsbConnectionController cleanup complete");
        });
    }
}