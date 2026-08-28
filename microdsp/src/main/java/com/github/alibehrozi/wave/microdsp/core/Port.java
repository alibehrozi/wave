package com.github.alibehrozi.wave.microdsp.core;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an input or output port for data flow between blocks.
 * Ports handle type negotiation, buffer management, and connection state.
 *
 * <p>
 * Supported connections:
 * <ul>
 * <li>One-to-one connections (single output to single input)</li>
 * <li>One-to-many connections (fan-out: one output to multiple inputs)</li>
 * </ul>
 *
 * <p>
 * Connection Rules:
 * <ul>
 * <li>OUTPUT port can connect to multiple INPUT ports (fan-out)</li>
 * <li>INPUT port can only connect to one OUTPUT port (no fan-in)</li>
 * <li>Must be from different blocks</li>
 * <li>Data types must be compatible</li>
 * </ul>
 */
public class Port implements AutoCloseable {

    private static final String TAG = "Port";

    /**
     * Port direction
     */
    public enum Direction {
        INPUT,
        OUTPUT
    }

    // Pointer to native Port object
    private long nativeHandle;
    private final String name;
    private final Direction direction;

    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Cache of connected ports for Java-side tracking
    private final List<Port> connectedPorts = new ArrayList<>();

    /**
     * Create a Java wrapper for an existing native port.
     * This constructor is package-private and should only be called by Block.
     * @param nativeHandle native port pointer (must not be 0)
     * @param name         port name
     * @param direction    port direction
     */
    public Port(long nativeHandle, @NonNull String name, @NonNull Direction direction) {
        if (nativeHandle == 0) {
            throw new IllegalArgumentException("Native handle cannot be 0");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Port name cannot be null or empty");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Port direction cannot be null");
        }
        this.nativeHandle = nativeHandle;
        this.name = name;
        this.direction = direction;
    }

    /**
     * Get the native handle for internal use
     * @return native object pointer
     * @throws IllegalStateException if port is closed
     */
    public long getNativeHandle() {
        checkNotClosed();
        return nativeHandle;
    }

    /**
     * Get port name
     * @return port name
     */
    public String getName() {
        return name;
    }

    /**
     * Get port direction
     * @return port direction
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Get current data type of this port
     * @return data type
     */
    public DataType getType() {
        checkNotClosed();
        synchronized (lock) {
            int typeIndex = nativeGetType(nativeHandle);
            if (typeIndex < 0 || typeIndex >= DataType.values().length) {
                throw new IllegalStateException("Invalid data type returned from native: " + typeIndex);
            }
            return DataType.values()[typeIndex];
        }
    }

    /**
     * Connect this port to another port.
     * Rules for connection:
     * <ul>
     * <li>One port must be INPUT and the other OUTPUT</li>
     * <li>Both ports must be from different blocks</li>
     * <li>Data types must be compatible</li>
     * </ul>
     * @param other port to connect to
     * @return true if connection successful
     * @throws IllegalStateException if this port is closed
     */
    public boolean connect(@NonNull Port other) {
        checkNotClosed();
        other.checkNotClosed();

        synchronized (lock) {
            // Validate connection rules
            if (!validateConnection(other)) {
                return false;
            }

            // Check for fan-in (INPUT cannot connect to multiple OUTPUTs)
            if (this.direction == Direction.INPUT && nativeIsConnected(nativeHandle)) {
                System.err.println("INPUT port '" + name + "' is already connected. " +
                        "INPUT ports do not support fan-in (multiple connections).");
                return false;
            }

            // For OUTPUT ports, multiple connections are allowed (fan-out)
            if (this.direction == Direction.OUTPUT) {
                // Check if already connected to this specific port
                if (isConnectedTo(other)) {
                    System.err.println("Already connected to port '" + other.name + "'");
                    return true; // Already connected, return success
                }
            }

            // Check if the other port is already connected (for INPUT ports)
            if (other.direction == Direction.INPUT && other.nativeIsConnected(other.nativeHandle)) {
                System.err.println("Destination INPUT port '" + other.name + "' is already connected. " +
                        "INPUT ports do not support fan-in.");
                return false;
            }

            // Check if connecting to itself
            if (this.nativeHandle == other.nativeHandle) {
                System.err.println("Cannot connect port to itself");
                return false;
            }

            // Check data type compatibility
            DataType thisType = getType();
            DataType otherType = other.getType();
            if (!areTypesCompatible(thisType, otherType)) {
                System.err.println("Incompatible data types: " + thisType + " and " + otherType);
                return false;
            }

            // Perform native connection
            boolean result = nativeConnect(nativeHandle, other.nativeHandle);
            if (result) {
                // Cache the connection
                connectedPorts.add(other);
                // Add this port to other's connected list if needed
                other.addConnectedPort(this);
            }
            return result;
        }
    }

    /**
     * Disconnect this port from all connected ports
     */
    public void disconnectAll() {
        checkNotClosed();
        synchronized (lock) {
            // Get all connections before disconnecting
            List<Port> connections = getConnections();
            for (Port connected : connections) {
                nativeDisconnectFrom(nativeHandle, connected.nativeHandle);
                connected.removeConnectedPort(this);
            }
            connectedPorts.clear();

            // Also clear any internal native connection state
            nativeDisconnect(nativeHandle);
        }
    }

    /**
     * Disconnect from a specific port
     * @param other port to disconnect from
     */
    public void disconnect(Port other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot disconnect from null port");
        }
        checkNotClosed();
        other.checkNotClosed();

        synchronized (lock) {
            if (!isConnectedTo(other)) {
                return; // Not connected
            }

            nativeDisconnectFrom(nativeHandle, other.nativeHandle);
            connectedPorts.remove(other);
            other.removeConnectedPort(this);
        }
    }

    /**
     * Check if port is connected
     * @return true if connected
     * @throws IllegalStateException if port is closed
     */
    public boolean isConnected() {
        checkNotClosed();
        synchronized (lock) {
            return !connectedPorts.isEmpty() || nativeIsConnected(nativeHandle);
        }
    }

    /**
     * Check if connected to a specific port.
     * @param other port to check connection with
     * @return true if connected to the specified port
     * @throws IllegalStateException if this port is closed
     */
    public boolean isConnectedTo(Port other) {
        if (other == null) {
            return false;
        }
        checkNotClosed();
        synchronized (lock) {
            return connectedPorts.contains(other) ||
                    nativeIsConnectedTo(nativeHandle, other.nativeHandle);
        }
    }

    /**
     * Get an unmodifiable list of all connected ports.
     * @return unmodifiable list of connected ports
     * @throws IllegalStateException if this port is closed
     */
    public List<Port> getConnections() {
        checkNotClosed();
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(connectedPorts));
        }
    }

    /**
     * Get the total number of active connections for this port.
     * @return connection count
     * @throws IllegalStateException if this port is closed
     */
    public int getConnectionCount() {
        checkNotClosed();
        synchronized (lock) {
            return connectedPorts.size();
        }
    }

    /**
     * Check if this port can connect to another port
     * @param other port to check
     * @return true if connection is valid and possible
     * @throws IllegalArgumentException if other is null
     * @throws IllegalStateException    if this port is closed
     */
    public boolean canConnectTo(@NonNull Port other) {
        try {
            checkNotClosed();
            other.checkNotClosed();

            // INPUT ports cannot have multiple connections
            if (this.direction == Direction.INPUT && this.isConnected()) {
                return false;
            }
            if (other.direction == Direction.INPUT && other.isConnected()) {
                return false;
            }

            return validateConnection(other) &&
                    areTypesCompatible(getType(), other.getType());
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Close this port and release native resources.
     * This will disconnect the port if connected.
     * This method is idempotent and thread-safe.
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (closed.getAndSet(true)) {
                return; // Already closed
            }

            // Disconnect all connections
            disconnectAll();

            // Destroy native object
            if (nativeHandle != 0) {
                nativeDestroyPort(nativeHandle);
                nativeHandle = 0;
            }
        }
    }

    /**
     * Check if this port is closed
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Get a string representation of this port
     * @return string representation
     */
    @SuppressLint("DefaultLocale")
    @NonNull
    @Override
    public String toString() {
        return String.format("Port{name='%s', direction=%s, type=%s, connections=%d}",
                name, direction, closed.get() ? "CLOSED" : getType(),
                closed.get() ? 0 : getConnectionCount());
    }

    /**
     * Check equality based on native handle
     * @param o object to compare
     * @return true if equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Port port = (Port) o;
        return nativeHandle == port.nativeHandle &&
                Objects.equals(name, port.name) &&
                direction == port.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nativeHandle, name, direction);
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Port '" + name + "' is closed");
        }
    }

    private boolean validateConnection(Port other) {
        // Must be different ports
        if (this == other) {
            System.err.println("Cannot connect port to itself");
            return false;
        }

        // TODO: Must be from different blocks

        // Must have opposite directions
        if (this.direction == other.direction) {
            System.err.println("Cannot connect ports with same direction: " + direction);
            return false;
        }

        return true;
    }

    private boolean areTypesCompatible(DataType type1, DataType type2) {
        // Same type is compatible
        if (type1 == type2) {
            return true;
        }

        // Numeric type compatibility
        if ((type1 == DataType.FLOAT || type1 == DataType.DOUBLE) &&
                (type2 == DataType.FLOAT || type2 == DataType.DOUBLE)) {
            return true;
        }

        if ((type1 == DataType.INT32 || type1 == DataType.SHORT || type1 == DataType.BYTE) &&
                (type2 == DataType.INT32 || type2 == DataType.SHORT || type2 == DataType.BYTE)) {
            return true;
        }

        // Complex to non-complex not allowed
        if (type1 == DataType.COMPLEX_FLOAT || type1 == DataType.COMPLEX_DOUBLE ||
                type2 == DataType.COMPLEX_FLOAT || type2 == DataType.COMPLEX_DOUBLE) {
            return false;
        }

        return false;
    }

    void addConnectedPort(Port port) {
        synchronized (lock) {
            if (!connectedPorts.contains(port)) {
                connectedPorts.add(port);
            }
        }
    }

    void removeConnectedPort(Port port) {
        synchronized (lock) {
            connectedPorts.remove(port);
        }
    }

    // Native methods
    private native boolean nativeConnect(long handle, long otherPortHandle);
    private native void nativeDisconnect(long handle);
    private native void nativeDisconnectFrom(long handle, long otherPortHandle);
    private native boolean nativeIsConnected(long handle);
    private native boolean nativeIsConnectedTo(long handle, long otherPortHandle);
    private native int nativeGetType(long handle);
    private native int nativeGetDirection(long handle);
    private native String nativeGetName(long handle);
    private native void nativeDestroyPort(long handle);

}