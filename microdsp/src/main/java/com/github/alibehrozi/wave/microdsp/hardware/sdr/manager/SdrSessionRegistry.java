package com.github.alibehrozi.wave.microdsp.hardware.sdr.manager;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.*;

import android.hardware.usb.UsbDevice;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and router for active SDR device sessions.
 *
 * <p>This component maintains active open SDR sessions, manages designated
 * RX and TX role assignments, and provides device filtering capabilities.</p>
 */
public final class SdrSessionRegistry {

    private static final String TAG = "SdrSessionRegistry";

    private final ConcurrentHashMap<UsbDevice, SdrSession> activeSessions = new ConcurrentHashMap<>();

    @Nullable
    private volatile UsbDevice rxUsbDevice;

    @Nullable
    private volatile UsbDevice txUsbDevice;

    /**
     * Checks if a session exists for the given USB device.
     * @param device USB device
     * @return {@code true} if an active session exists
     */
    public boolean containsDevice(@NonNull UsbDevice device) {
        return activeSessions.containsKey(device);
    }

    /**
     * Retrieves an active session by USB device.
     * @param device USB device
     * @return active session, or {@code null}
     */
    @Nullable
    public SdrSession getSession(@NonNull UsbDevice device) {
        return activeSessions.get(device);
    }

    /**
     * Adds an active session and automatically assigns default RX/TX roles if unassigned.
     * @param session session to add
     */
    public void addSession(@NonNull SdrSession session) {
        UsbDevice device = session.getUsbDevice();
        activeSessions.put(device, session);

        SdrDeviceInfo createdDeviceInfo = session.getDeviceInfo();
        if (rxUsbDevice == null && (createdDeviceInfo == null || createdDeviceInfo.supportsRx())) {
            rxUsbDevice = device;
        }
        if (txUsbDevice == null && (createdDeviceInfo == null || createdDeviceInfo.supportsTx())) {
            txUsbDevice = device;
        }
    }

    /**
     * Removes a session by USB device and adjusts RX/TX fallbacks.
     * @param device USB device to remove
     * @return removed session, or {@code null}
     */
    @Nullable
    public SdrSession removeSession(@NonNull UsbDevice device) {
        SdrSession session = activeSessions.remove(device);
        if (rxUsbDevice == device) {
            rxUsbDevice = findFallbackRxDeviceExcept(device);
        }
        if (txUsbDevice == device) {
            txUsbDevice = findFallbackTxDeviceExcept(device);
        }
        return session;
    }

    /**
     * Returns an unmodifiable list of all active sessions.
     * @return active session list
     */
    @NonNull
    public List<SdrSession> getActiveSessions() {
        return Collections.unmodifiableList(new ArrayList<>(activeSessions.values()));
    }

    /**
     * Returns the total count of active SDR sessions.
     * @return active session count
     */
    public int size() {
        return activeSessions.size();
    }

    /**
     * Checks whether there are no active sessions.
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return activeSessions.isEmpty();
    }

    /**
     * Returns all connected SDR device instances.
     * @return list of connected SDR devices
     */
    @NonNull
    public List<SdrDevice> getConnectedDevices() {
        List<SdrDevice> devices = new ArrayList<>();
        for (SdrSession session : activeSessions.values()) {
            if (session.getDevice().isConnected()) {
                devices.add(session.getDevice());
            }
        }
        return devices;
    }

    /**
     * Returns all connected SDR device instances that support reception.
     * @return list of RX-capable SDR devices
     */
    @NonNull
    public List<SdrDevice> getDevicesSupportingRx() {
        List<SdrDevice> devices = new ArrayList<>();
        for (SdrSession session : activeSessions.values()) {
            SdrDevice device = session.getDevice();
            SdrDeviceInfo info = session.getDeviceInfo();
            if (device.isConnected() && (info == null || info.supportsRx())) {
                devices.add(device);
            }
        }
        return devices;
    }

    /**
     * Returns all connected SDR device instances that support transmission.
     * @return list of TX-capable SDR devices
     */
    @NonNull
    public List<SdrDevice> getDevicesSupportingTx() {
        List<SdrDevice> devices = new ArrayList<>();
        for (SdrSession session : activeSessions.values()) {
            SdrDevice device = session.getDevice();
            SdrDeviceInfo info = session.getDeviceInfo();
            if (device.isConnected() && (info == null || info.supportsTx())) {
                devices.add(device);
            }
        }
        return devices;
    }

    /**
     * Gets the SDR assigned for reception (RX).
     * @return RX SDR, or fallback connected SDR, or {@code null}
     */
    @Nullable
    public SdrDevice getRxDevice() {
        if (rxUsbDevice != null) {
            SdrSession session = activeSessions.get(rxUsbDevice);
            if (session != null && session.getDevice().isConnected()) {
                return session.getDevice();
            }
        }
        for (SdrSession session : activeSessions.values()) {
            SdrDeviceInfo info = session.getDeviceInfo();
            if (session.getDevice().isConnected() && (info == null || info.supportsRx())) {
                return session.getDevice();
            }
        }
        return getCurrentDeviceFallback();
    }

    /**
     * Sets the SDR device designated for reception.
     * @param sdr SDR instance to designate for RX
     */
    public void setRxDevice(@Nullable SdrDevice sdr) {
        if (sdr == null) {
            rxUsbDevice = null;
            return;
        }
        SdrSession session = findSessionForDevice(sdr);
        if (session != null) {
            rxUsbDevice = session.getUsbDevice();
            if (session.getRole() == SdrRole.TX_ONLY) {
                session.setRole(SdrRole.HALF_DUPLEX);
            } else if (session.getRole() == SdrRole.UNASSIGNED) {
                session.setRole(SdrRole.RX_ONLY);
            }
        }
    }

    /**
     * Gets the SDR assigned for transmission (TX).
     * @return TX SDR, or fallback connected SDR, or {@code null}
     */
    @Nullable
    public SdrDevice getTxDevice() {
        if (txUsbDevice != null) {
            SdrSession session = activeSessions.get(txUsbDevice);
            if (session != null && session.getDevice().isConnected()) {
                return session.getDevice();
            }
        }
        for (SdrSession session : activeSessions.values()) {
            SdrDeviceInfo info = session.getDeviceInfo();
            if (session.getDevice().isConnected() && (info == null || info.supportsTx())) {
                return session.getDevice();
            }
        }
        return getCurrentDeviceFallback();
    }

    /**
     * Sets the SDR device designated for transmission.
     * @param sdr SDR instance to designate for TX
     */
    public void setTxDevice(@Nullable SdrDevice sdr) {
        if (sdr == null) {
            txUsbDevice = null;
            return;
        }
        SdrSession session = findSessionForDevice(sdr);
        if (session != null) {
            txUsbDevice = session.getUsbDevice();
            if (session.getRole() == SdrRole.RX_ONLY) {
                session.setRole(SdrRole.HALF_DUPLEX);
            } else if (session.getRole() == SdrRole.UNASSIGNED) {
                session.setRole(SdrRole.TX_ONLY);
            }
        }
    }

    /**
     * Assigns a logical role to a specific USB SDR device.
     * @param device USB device
     * @param role   role to assign
     */
    public void setRole(@NonNull UsbDevice device, @NonNull SdrRole role) {
        SdrSession session = activeSessions.get(device);
        if (session != null) {
            session.setRole(role);
            if (role.supportsRx()) {
                rxUsbDevice = device;
            }
            if (role.supportsTx()) {
                txUsbDevice = device;
            }
        }
    }

    @Nullable
    private SdrDevice getCurrentDeviceFallback() {
        for (SdrSession session : activeSessions.values()) {
            if (session.getDevice().isConnected()) {
                return session.getDevice();
            }
        }
        return null;
    }

    /**
     * Finds the session enclosing the given SDR device instance.
     * @param device SDR device instance
     * @return session, or {@code null}
     */
    @Nullable
    public SdrSession findSessionForDevice(@NonNull SdrDevice device) {
        for (SdrSession session : activeSessions.values()) {
            if (session.getDevice() == device) {
                return session;
            }
        }
        return null;
    }

    /**
     * Closes all active SDR instances safely and clears all session records.
     * @param sdrLock object lock for synchronized device operations
     */
    public void closeAll(@NonNull Object sdrLock) {
        synchronized (sdrLock) {
            for (SdrSession session : activeSessions.values()) {
                safeClose(session.getDevice());
            }
            activeSessions.clear();
            rxUsbDevice = null;
            txUsbDevice = null;
        }
    }

    @Nullable
    private UsbDevice findFallbackRxDeviceExcept(@NonNull UsbDevice exclude) {
        for (Map.Entry<UsbDevice, SdrSession> entry : activeSessions.entrySet()) {
            if (!entry.getKey().equals(exclude)) {
                SdrDeviceInfo info = entry.getValue().getDeviceInfo();
                if (info == null || info.supportsRx()) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Nullable
    private UsbDevice findFallbackTxDeviceExcept(@NonNull UsbDevice exclude) {
        for (Map.Entry<UsbDevice, SdrSession> entry : activeSessions.entrySet()) {
            if (!entry.getKey().equals(exclude)) {
                SdrDeviceInfo info = entry.getValue().getDeviceInfo();
                if (info == null || info.supportsTx()) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private void safeClose(@NonNull SdrDevice sdr) {
        try {
            sdr.close();
        } catch (RuntimeException e) {
            Log.e(TAG, "Error closing SDR instance", e);
        }
    }
}
