package com.github.alibehrozi.wave.microdsp.core;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Base class for all DSP processing blocks in the system.
 * Represents a signal processing component with input/output ports and
 * parameters.
 *
 * <p>
 * A Block is the fundamental processing unit in the DSP system. Each block:
 * <ul>
 * <li>Has a unique name for identification</li>
 * <li>Contains input and output ports for data flow</li>
 * <li>Supports integer, double, string, and boolean parameters</li>
 * <li>Can be started, stopped, and checked for active state</li>
 * <li>Provides JNI bridge to native C++ implementations</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * // Create a custom block
 * class MyFilter extends Block {
 *     public MyFilter(String name) {
 *         super(name);
 *     }
 *
 *     @Override
 *     protected long nativeCreateBlock(String name) {
 *         return nativeCreateMyFilter(name);
 *     }
 *
 *     private native long nativeCreateMyFilter(String name);
 * }
 *
 * // Use the block
 * MyFilter filter = new MyFilter("eq_filter");
 * filter.setDoubleParameter("frequency", 1000.0);
 * filter.setIntParameter("order", 4);
 * filter.start();
 * }</pre>
 */
public abstract class Block implements AutoCloseable {

    // Pointer to native Block object
    protected long nativeHandle;
    private final String name;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Cache for ports to avoid repeated native calls
    private final Map<String, Port> inputPortCache = new HashMap<>();
    private final Map<String, Port> outputPortCache = new HashMap<>();
    private volatile boolean portsCached = false;

    /**
     * Create a new block with the given name.
     * Subclasses must call this constructor and implement
     * {@link #nativeCreateBlock(String)}.
     * @param name block name (must be unique within a flowgraph)
     * @throws IllegalArgumentException if name is null or empty
     * @throws RuntimeException         if native block creation fails
     */
    protected Block(@NonNull String name) {
        this.name = name;
        this.nativeHandle = nativeCreateBlock(name);

        // Subclasses that override nativeCreateBlock might not have initialized their
        // nativeHandle yet
        // if they are doing it in their own constructor.
        // In that case, nativeHandle here will be 0, and we check later or allow manual
        // setting.
    }

    /**
     * Alternative constructor for subclasses that create the native block before
     * calling super.
     * @param name   block name
     * @param handle native handle
     */
    protected Block(@NonNull String name, long handle) {
        this.name = name;
        this.nativeHandle = handle;
        if (handle == 0) {
            throw new RuntimeException("Failed to create native block: " + name);
        }
    }

    /**
     * Get the unique name of this block
     * @return block name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the native handle for JNI operations
     * @return native object pointer
     * @throws IllegalStateException if block is closed
     */
    public long getNativeHandle() {
        checkNotClosed();
        return nativeHandle;
    }

    /**
     * Start processing in this block.
     * This method is idempotent - calling start on an already started block has no
     * effect.
     * @return true if started successfully (or already started)
     * @throws IllegalStateException if block is closed
     */
    public boolean start() {
        checkNotClosed();

        lock.writeLock().lock();
        try {
            if (started.get()) {
                return true;
            }

            boolean result = nativeStart(nativeHandle);
            if (result) {
                started.set(true);
            }
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Stop processing in this block.
     * This method is idempotent - calling stop on an already stopped block has no
     * effect.
     * @throws IllegalStateException if block is closed
     */
    public void stop() {
        checkNotClosed();

        lock.writeLock().lock();
        try {
            if (!started.getAndSet(false)) {
                return; // Already stopped
            }

            nativeStop(nativeHandle);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if block is currently active (processing)
     * @return true if active
     * @throws IllegalStateException if block is closed
     */
    public boolean isActive() {
        checkNotClosed();

        lock.readLock().lock();
        try {
            return nativeIsActive(nativeHandle);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get number of input ports
     * @return input port count
     * @throws IllegalStateException if block is closed
     */
    public int getInputPortCount() {
        checkNotClosed();

        lock.readLock().lock();
        try {
            return nativeGetInputPortCount(nativeHandle);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get number of output ports
     * @return output port count
     * @throws IllegalStateException if block is closed
     */
    public int getOutputPortCount() {
        checkNotClosed();

        lock.readLock().lock();
        try {
            return nativeGetOutputPortCount(nativeHandle);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get input port by name
     * @param name port name (must not be null or empty)
     * @return Port object or null if not found
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public Port getInputPort(@NonNull String name) {
        checkNotClosed();

        lock.readLock().lock();
        try {
            // Check cache first
            Port cached = inputPortCache.get(name);
            if (cached != null && !cached.isClosed()) {
                return cached;
            }

            long portHandle = nativeGetInputPort(nativeHandle, name);
            if (portHandle == 0) {
                return null;
            }

            Port port = createPortWrapper(portHandle, Port.Direction.INPUT, name);
            inputPortCache.put(name, port);
            return port;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get input port by index
     * @param index port index (0-based)
     * @return Port object or null if index is invalid
     * @throws IllegalArgumentException if index is negative
     * @throws IllegalStateException    if block is closed
     */
    public Port getInputPort(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Port index cannot be negative");
        }
        checkNotClosed();

        lock.readLock().lock();
        try {
            String portName = nativeGetInputPortName(nativeHandle, index);
            if (portName == null || portName.isEmpty()) {
                return null;
            }
            return getInputPort(portName);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get output port by name
     * @param name port name (must not be null or empty)
     * @return Port object or null if not found
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public Port getOutputPort(@NonNull String name) {
        checkNotClosed();

        lock.readLock().lock();
        try {
            // Check cache first
            Port cached = outputPortCache.get(name);
            if (cached != null && !cached.isClosed()) {
                return cached;
            }

            long portHandle = nativeGetOutputPort(nativeHandle, name);
            if (portHandle == 0) {
                return null;
            }

            Port port = createPortWrapper(portHandle, Port.Direction.OUTPUT, name);
            outputPortCache.put(name, port);
            return port;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get output port by index
     * @param index port index (0-based)
     * @return Port object or null if index is invalid
     * @throws IllegalArgumentException if index is negative
     * @throws IllegalStateException    if block is closed
     */
    public Port getOutputPort(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Port index cannot be negative");
        }
        checkNotClosed();

        lock.readLock().lock();
        try {
            String portName = nativeGetOutputPortName(nativeHandle, index);
            if (portName == null || portName.isEmpty()) {
                return null;
            }
            return getOutputPort(portName);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all input ports
     * @return unmodifiable list of input ports
     * @throws IllegalStateException if block is closed
     */
    public List<Port> getInputPorts() {
        checkNotClosed();

        lock.readLock().lock();
        try {
            int count = getInputPortCount();
            List<Port> ports = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Port port = getInputPort(i);
                if (port != null) {
                    ports.add(port);
                }
            }
            return Collections.unmodifiableList(ports);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all output ports
     * @return unmodifiable list of output ports
     * @throws IllegalStateException if block is closed
     */
    public List<Port> getOutputPorts() {
        checkNotClosed();

        lock.readLock().lock();
        try {
            int count = getOutputPortCount();
            List<Port> ports = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Port port = getOutputPort(i);
                if (port != null) {
                    ports.add(port);
                }
            }
            return Collections.unmodifiableList(ports);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if a port exists
     * @param portName  port name
     * @param direction port direction
     * @return true if port exists
     */
    public boolean hasPort(@NonNull String portName, Port.Direction direction) {
        if (direction == Port.Direction.INPUT) {
            return getInputPort(portName) != null;
        } else {
            return getOutputPort(portName) != null;
        }
    }

    // Parameter Methods

    /**
     * Set integer parameter
     * @param name  parameter name (must not be null or empty)
     * @param value parameter value
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public void setIntParameter(@NonNull String name, int value) {
        checkNotClosed();

        lock.writeLock().lock();
        try {
            nativeSetIntParameter(nativeHandle, name, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Set double parameter
     * @param name  parameter name (must not be null or empty)
     * @param value parameter value
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public void setDoubleParameter(@NonNull String name, double value) {
        checkNotClosed();

        lock.writeLock().lock();
        try {
            nativeSetDoubleParameter(nativeHandle, name, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Set string parameter
     * @param name  parameter name (must not be null or empty)
     * @param value parameter value
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public void setStringParameter(@NonNull String name, @NonNull String value) {
        checkNotClosed();

        lock.writeLock().lock();
        try {
            nativeSetStringParameter(nativeHandle, name, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Set boolean parameter
     * @param name  parameter name (must not be null or empty)
     * @param value parameter value
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public void setBooleanParameter(@NonNull String name, boolean value) {
        checkNotClosed();

        lock.writeLock().lock();
        try {
            nativeSetBooleanParameter(nativeHandle, name, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get integer parameter with default
     * @param name         parameter name (must not be null or empty)
     * @param defaultValue default value if not found
     * @return parameter value
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public int getIntParameter(@NonNull String name, int defaultValue) {
        checkNotClosed();

        lock.readLock().lock();
        try {
            return nativeGetIntParameter(nativeHandle, name, defaultValue);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get double parameter with default
     * @param name         parameter name (must not be null or empty)
     * @param defaultValue default value if not found
     * @return parameter value
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public double getDoubleParameter(@NonNull String name, double defaultValue) {
        checkNotClosed();

        lock.readLock().lock();
        try {
            return nativeGetDoubleParameter(nativeHandle, name, defaultValue);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get string parameter with default
     * @param name         parameter name (must not be null or empty)
     * @param defaultValue default value if not found
     * @return parameter value
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public String getStringParameter(@NonNull String name, String defaultValue) {
        checkNotClosed();

        lock.readLock().lock();
        try {
            return nativeGetStringParameter(nativeHandle, name, defaultValue);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get boolean parameter with default
     * @param name         parameter name (must not be null or empty)
     * @param defaultValue default value if not found
     * @return parameter value
     * @throws IllegalArgumentException if name is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public boolean getBooleanParameter(@NonNull String name, boolean defaultValue) {
        checkNotClosed();

        lock.readLock().lock();
        try {
            return nativeGetBooleanParameter(nativeHandle, name, defaultValue);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if a parameter exists
     * @param name parameter name
     * @return true if parameter exists
     */
    public boolean hasParameter(@NonNull String name) {
        checkNotClosed();

        // Try to get the parameter, if it returns the default value
        // this doesn't necessarily mean it doesn't exist, so we need
        // a dedicated native method
        return nativeHasParameter(nativeHandle, name);
    }

    /**
     * Get all parameter names
     * @return list of parameter names
     * @throws IllegalStateException if block is closed
     */
    public List<String> getParameterNames() {
        checkNotClosed();

        lock.readLock().lock();
        try {
            String[] names = nativeGetParameterNames(nativeHandle);
            return Collections.unmodifiableList(Arrays.asList(names));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reset all parameters to default values
     * @throws IllegalStateException if block is closed
     */
    public void resetParameters() {
        checkNotClosed();

        lock.writeLock().lock();
        try {
            nativeResetParameters(nativeHandle);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Create a Java Port wrapper for a native port.
     * Subclasses can override this to provide custom Port implementations.
     * @param portHandle native port pointer
     * @param direction  port direction
     * @param name       port name
     * @return Port wrapper
     */
    protected Port createPortWrapper(long portHandle, Port.Direction direction, String name) {
        return new Port(portHandle, name, direction);
    }

    /**
     * Close the block and release native resources.
     * This will stop the block if running and release all resources.
     * This method is idempotent and thread-safe.
     */
    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (closed.getAndSet(true)) {
                return; // Already closed
            }

            // Stop if running
            if (started.get()) {
                nativeStop(nativeHandle);
                started.set(false);
            }

            // Destroy native object
            if (nativeHandle != 0) {
                nativeDestroyBlock(nativeHandle);
                nativeHandle = 0;
            }

            // Clear caches
            inputPortCache.clear();
            outputPortCache.clear();
            portsCached = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if this block is closed
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Check if this block is started
     * @return true if started
     */
    public boolean isStarted() {
        return started.get();
    }

    /**
     * Get a string representation of this block
     * @return string representation
     */
    @NonNull
    @SuppressLint("DefaultLocale")
    @Override
    public String toString() {
        return String.format("Block{name='%s', nativeHandle=0x%x, started=%s, closed=%s, inputs=%d, outputs=%d}",
                name, nativeHandle, started.get(), closed.get(),
                getInputPortCount(), getOutputPortCount());
    }

    // Private helper methods

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Block '" + name + "' is closed");
        }
    }

    /**
     * Create a native block implementation.
     * Optional for subclasses that pass native handle to super(name, handle).
     * @param name block name
     * @return native object pointer
     */
    protected long nativeCreateBlock(String name) {
        return 0;
    }

    // Native methods that all blocks share
    protected native boolean nativeStart(long handle);
    protected native void nativeStop(long handle);
    protected native boolean nativeIsActive(long handle);
    protected native int nativeGetInputPortCount(long handle);
    protected native int nativeGetOutputPortCount(long handle);
    protected native long nativeGetInputPort(long handle, String name);
    protected native long nativeGetOutputPort(long handle, String name);
    protected native String nativeGetInputPortName(long handle, int index);
    protected native String nativeGetOutputPortName(long handle, int index);

    // Parameter native methods
    protected native void nativeSetIntParameter(long handle, String name, int value);
    protected native void nativeSetDoubleParameter(long handle, String name, double value);
    protected native void nativeSetStringParameter(long handle, String name, String value);
    protected native void nativeSetBooleanParameter(long handle, String name, boolean value);
    protected native int nativeGetIntParameter(long handle, String name, int defaultValue);
    protected native double nativeGetDoubleParameter(long handle, String name, double defaultValue);
    protected native String nativeGetStringParameter(long handle, String name, String defaultValue);
    protected native boolean nativeGetBooleanParameter(long handle, String name, boolean defaultValue);
    protected native boolean nativeHasParameter(long handle, String name);
    protected native String[] nativeGetParameterNames(long handle);
    protected native void nativeResetParameters(long handle);

    // Resource management
    protected native void nativeDestroyBlock(long handle);

}