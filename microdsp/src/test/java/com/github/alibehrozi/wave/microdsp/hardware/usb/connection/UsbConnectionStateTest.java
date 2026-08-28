package com.github.alibehrozi.wave.microdsp.hardware.usb.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UsbConnectionStateTest {

    @Test
    public void testDefaultState() {
        UsbConnectionState state = new UsbConnectionState();

        assertEquals(UsbConnectionState.State.DISCONNECTED, state.getState());
        assertEquals(UsbConnectionState.State.DISCONNECTED, state.getPreviousState());
        assertFalse(state.isConnected());
        assertFalse(state.isConnecting());
        assertTrue(state.isDisconnected());
        assertFalse(state.isError());
        assertFalse(state.isActive());
        assertEquals(0, state.getConnectionAttempts());
        assertEquals(0, state.getReconnectionAttempts());
        assertFalse(state.isAutoConnect());
    }

    @Test
    public void testStateTransitions() {
        UsbConnectionState state = new UsbConnectionState();

        state.setState(UsbConnectionState.State.CONNECTING);
        assertEquals(UsbConnectionState.State.CONNECTING, state.getState());
        assertEquals(UsbConnectionState.State.DISCONNECTED, state.getPreviousState());
        assertTrue(state.isConnecting());
        assertTrue(state.isActive());

        state.setState(UsbConnectionState.State.CONNECTED);
        assertEquals(UsbConnectionState.State.CONNECTED, state.getState());
        assertEquals(UsbConnectionState.State.CONNECTING, state.getPreviousState());
        assertTrue(state.isConnected());
        assertTrue(state.isActive());
        assertTrue(state.getConnectedAt() > 0);

        state.setState(UsbConnectionState.State.DISCONNECTED);
        assertEquals(UsbConnectionState.State.DISCONNECTED, state.getState());
        assertEquals(UsbConnectionState.State.CONNECTED, state.getPreviousState());
        assertTrue(state.isDisconnected());
        assertFalse(state.isActive());
        assertTrue(state.getDisconnectedAt() > 0);
    }

    @Test
    public void testErrorHandling() {
        UsbConnectionState state = new UsbConnectionState();

        state.setError(UsbConnectionState.ErrorType.PERMISSION_DENIED, -1, "Permission Denied");
        assertTrue(state.isError());
        assertTrue(state.hasError());
        assertEquals(UsbConnectionState.ErrorType.PERMISSION_DENIED, state.getErrorType());
        assertEquals(-1, state.getErrorCode());
        assertEquals("Permission Denied", state.getErrorMessage());

        state.clearError();
        assertFalse(state.hasError());
        assertEquals(UsbConnectionState.ErrorType.NONE, state.getErrorType());
        assertEquals(0, state.getErrorCode());
        assertNull(state.getErrorMessage());
    }

    @Test
    public void testCountersAndAutoConnect() {
        UsbConnectionState state = new UsbConnectionState();

        state.incrementConnectionAttempts();
        state.incrementConnectionAttempts();
        assertEquals(2, state.getConnectionAttempts());

        state.incrementReconnectionAttempts();
        assertEquals(1, state.getReconnectionAttempts());

        state.setAutoConnect(true);
        assertTrue(state.isAutoConnect());

        state.resetAttempts();
        assertEquals(0, state.getConnectionAttempts());
        assertEquals(0, state.getReconnectionAttempts());
    }

    @Test
    public void testCopy() {
        UsbConnectionState original = new UsbConnectionState();
        original.setState(UsbConnectionState.State.CONNECTING);
        original.setAutoConnect(true);
        original.incrementConnectionAttempts();

        UsbConnectionState copy = original.copy();
        assertEquals(original.getState(), copy.getState());
        assertEquals(original.isAutoConnect(), copy.isAutoConnect());
        assertEquals(original.getConnectionAttempts(), copy.getConnectionAttempts());

        // Mutating original should not change copy
        original.setState(UsbConnectionState.State.CONNECTED);
        assertEquals(UsbConnectionState.State.CONNECTING, copy.getState());
    }

    @Test
    public void testStatusString() {
        UsbConnectionState state = new UsbConnectionState();
        assertNotNull(state.getStatusString());
        assertTrue(state.getStatusString().contains("DISCONNECTED"));

        state.setError(UsbConnectionState.ErrorType.TIMEOUT, -2, "Device timeout");
        assertTrue(state.getStatusString().contains("TIMEOUT"));
        assertTrue(state.getStatusString().contains("Device timeout"));
    }
}
