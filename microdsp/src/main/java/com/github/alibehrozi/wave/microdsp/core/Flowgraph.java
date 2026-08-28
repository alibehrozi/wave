package com.github.alibehrozi.wave.microdsp.core;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a signal processing flowgraph consisting of connected DSP blocks.
 * A flowgraph is a container that manages the lifecycle, connections, and
 * execution of a chain of Block objects.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (Flowgraph flowgraph = new Flowgraph("audio_chain")) {
 *     Block source = new AudioSource("mic");
 *     Block filter = new LowPassFilter("filter");
 *     Block sink = new AudioSink("speaker");
 *
 *     flowgraph.addBlock(source);
 *     flowgraph.addBlock(filter);
 *     flowgraph.addBlock(sink);
 *
 *     flowgraph.connect(source, filter);
 *     flowgraph.connect(filter, sink);
 *
 *     flowgraph.start();
 *     flowgraph.waitForCompletion();
 * }
 * }</pre>
 */
public class Flowgraph implements AutoCloseable {

    private static final String TAG = "Flowgraph";

    // Pointer to native Flowgraph object
    private long nativeHandle;
    private final String name;
    private final List<Block> blocks;
    private volatile FlowgraphListener listener;

    private final Object lock = new Object();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Default port names for convenience connections
    private String defaultOutputPort = "out";
    private String defaultInputPort = "in";

    /**
     * Listener interface for flowgraph state changes
     */
    public interface FlowgraphListener {
        /**
         * Called when flowgraph starts successfully
         * @param flowgraph the flowgraph that started
         */
        void onFlowgraphStarted(Flowgraph flowgraph);

        /**
         * Called when flowgraph stops
         * @param flowgraph the flowgraph that stopped
         */
        void onFlowgraphStopped(Flowgraph flowgraph);

        /**
         * Called when flowgraph encounters an error
         * @param flowgraph the flowgraph with error
         * @param error     the exception that occurred
         */
        void onFlowgraphError(Flowgraph flowgraph, String error);
    }

    /**
     * Create a new flowgraph
     * @param name flowgraph name (must be unique within a system)
     */
    public Flowgraph(@NonNull String name) {
        this.name = name;
        this.blocks = new ArrayList<>();
        this.nativeHandle = nativeCreateFlowgraph(name);

        if (this.nativeHandle == 0) {
            throw new RuntimeException("Failed to create native flowgraph: " + name);
        }
    }

    /**
     * Get the unique name of this flowgraph
     * @return flowgraph name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the native handle for JNI operations
     * @return native object pointer
     */
    public long getNativeHandle() {
        return nativeHandle;
    }

    /**
     * Configure default port names for convenience connect() method
     * @param outputPort default output port name (e.g., "out")
     * @param inputPort  default input port name (e.g., "in")
     * @return this flowgraph for method chaining
     */
    public Flowgraph withDefaultPorts(@NonNull String outputPort, @NonNull String inputPort) {
        if (outputPort.trim().isEmpty()) {
            throw new IllegalArgumentException("Output port name cannot be null or empty");
        }
        if (inputPort.trim().isEmpty()) {
            throw new IllegalArgumentException("Input port name cannot be null or empty");
        }

        this.defaultOutputPort = outputPort;
        this.defaultInputPort = inputPort;
        return this;
    }

    /**
     * Add a block to this flowgraph
     * @param block block to add
     */
    public void addBlock(@NonNull Block block) {
        checkNotClosed();
        synchronized (lock) {
            if (blocks.contains(block)) {
                return; // Block already added
            }

            nativeAddBlock(nativeHandle, block.getNativeHandle());
            blocks.add(block);
        }
    }

    /**
     * Remove a block from this flowgraph
     * @param block block to remove
     */
    public void removeBlock(@NonNull Block block) {
        checkNotClosed();
        synchronized (lock) {
            if (!blocks.contains(block)) {
                return; // Block not in flowgraph
            }

            // Stop the block if flowgraph is running
            if (started.get()) {
                block.stop();
            }

            nativeRemoveBlock(nativeHandle, block.getNativeHandle());
            blocks.remove(block);
        }
    }

    /**
     * Connect two blocks using default port names
     * @param srcBlock source block
     * @param dstBlock destination block
     * @return true if connection successful
     */
    public boolean connect(@NonNull Block srcBlock, @NonNull Block dstBlock) {
        checkNotClosed();
        return connect(srcBlock, defaultOutputPort, dstBlock, defaultInputPort);
    }

    /**
     * Connect specific ports of two blocks
     * @param srcBlock source block
     * @param srcPort  source port name
     * @param dstBlock destination block
     * @param dstPort  destination port name
     * @return true if connection successful
     */
    public boolean connect(@NonNull Block srcBlock, @NonNull String srcPort,
                           @NonNull Block dstBlock, @NonNull String dstPort) {
        checkNotClosed();
        synchronized (lock) {
            // Validate and ensure blocks are in this flowgraph
            if (!blocks.contains(srcBlock)) {
                addBlock(srcBlock);
            }
            if (!blocks.contains(dstBlock)) {
                addBlock(dstBlock);
            }

            // Validate ports exist before native call
            Port srcPortObj = srcBlock.getOutputPort(srcPort);
            Port dstPortObj = dstBlock.getInputPort(dstPort);

            if (srcPortObj == null) {
                notifyError("Source port not found: " + srcPort + " in block " + srcBlock.getName());
                return false;
            }
            if (dstPortObj == null) {
                notifyError("Destination port not found: " + dstPort + " in block " + dstBlock.getName());
                return false;
            }

            // Check if already connected
            if (srcPortObj.isConnected()) {
                notifyError("Source port already connected: " + srcPort);
                return false;
            }

            return nativeConnect(nativeHandle,
                    srcBlock.getNativeHandle(), srcPort,
                    dstBlock.getNativeHandle(), dstPort);
        }
    }

    /**
     * Disconnect two blocks using default port names
     * @param srcBlock source block
     * @param dstBlock destination block
     */
    public void disconnect(@NonNull Block srcBlock, @NonNull Block dstBlock) {
        checkNotClosed();
        disconnect(srcBlock, defaultOutputPort, dstBlock, defaultInputPort);
    }

    /**
     * Disconnect specific ports of two blocks
     * @param srcBlock source block
     * @param srcPort  source port name
     * @param dstBlock destination block
     * @param dstPort  destination port name
     */
    public void disconnect(@NonNull Block srcBlock, @NonNull String srcPort,
                           @NonNull Block dstBlock, @NonNull String dstPort) {
        checkNotClosed();
        synchronized (lock) {
            nativeDisconnect(nativeHandle,
                    srcBlock.getNativeHandle(), srcPort,
                    dstBlock.getNativeHandle(), dstPort);
        }
    }

    /**
     * Start the flowgraph (non-blocking)
     * @return true if started successfully
     */
    public boolean start() {
        checkNotClosed();
        synchronized (lock) {
            if (started.get()) {
                return true;
            }

            if (blocks.isEmpty()) {
                notifyError("Cannot start empty flowgraph");
                return false;
            }

            try {
                boolean result = nativeStart(nativeHandle);
                if (result) {
                    started.set(true);
                    notifyStarted();
                } else {
                    notifyError("Failed to start flowgraph: " + name);
                }
                return result;
            } catch (Exception e) {
                notifyError(e.getMessage());
                return false;
            }
        }
    }

    /**
     * Stop the flowgraph
     */
    public void stop() {
        checkNotClosed();
        synchronized (lock) {
            if (!started.getAndSet(false)) {
                return; // Already stopped
            }

            nativeStop(nativeHandle);
            notifyStopped();
        }
    }

    /**
     * Wait for flowgraph to complete (blocking)
     */
    public void waitForCompletion() {
        checkNotClosed();
        synchronized (lock) {
            if (!started.get()) {
                return;
            }

            nativeWaitForCompletion(nativeHandle);
        }
    }

    /**
     * Run the flowgraph and wait for completion (blocking)
     */
    public void run() {
        checkNotClosed();

        synchronized (lock) {
            if (started.get()) {
                notifyError("Flowgraph is already running");
                return;
            }

            if (blocks.isEmpty()) {
                notifyError("Cannot run empty flowgraph");
                return;
            }

            nativeRun(nativeHandle);
            started.set(false);
        }
    }

    /**
     * Check if flowgraph is running
     * @return true if running
     */
    public boolean isRunning() {
        checkNotClosed();
        return started.get() && nativeIsRunning(nativeHandle);
    }

    /**
     * Check if flowgraph is closed
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Set flowgraph state listener
     * @param listener state listener (can be null to remove listener)
     */
    public void setListener(FlowgraphListener listener) {
        this.listener = listener;
    }

    /**
     * Get all blocks in this flowgraph
     * @return unmodifiable list of blocks
     */
    public List<Block> getBlocks() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(blocks));
        }
    }

    /**
     * Get number of blocks in flowgraph
     * @return block count
     */
    public int getBlockCount() {
        synchronized (lock) {
            return blocks.size();
        }
    }

    /**
     * Check if a block is in this flowgraph
     * @param block block to check
     * @return true if block is in flowgraph
     */
    public boolean hasBlock(Block block) {
        if (block == null) {
            return false;
        }
        synchronized (lock) {
            return blocks.contains(block);
        }
    }

    /**
     * Check if two blocks are connected
     * @param srcBlock source block
     * @param dstBlock destination block
     * @return true if connected
     */
    public boolean areConnected(@NonNull Block srcBlock, @NonNull Block dstBlock) {
        return areConnected(srcBlock, defaultOutputPort, dstBlock, defaultInputPort);
    }

    /**
     * Check if specific ports of two blocks are connected
     * @param srcBlock source block
     * @param srcPort  source port name
     * @param dstBlock destination block
     * @param dstPort  destination port name
     * @return true if connected
     */
    public boolean areConnected(@NonNull Block srcBlock, @NonNull String srcPort,
                                @NonNull Block dstBlock, @NonNull String dstPort) {
        synchronized (lock) {
            Port srcPortObj = srcBlock.getOutputPort(srcPort);
            if (srcPortObj == null || !srcPortObj.isConnected()) {
                return false;
            }

            // Note: This is a simplified check. A more thorough check would
            // verify the actual connection in native code
            return nativeAreConnected(nativeHandle,
                    srcBlock.getNativeHandle(), srcPort,
                    dstBlock.getNativeHandle(), dstPort);
        }
    }

    /**
     * Remove all blocks and connections from this flowgraph
     * @throws IllegalStateException if flowgraph is running
     */
    public void clear() {
        synchronized (lock) {
            if (started.get()) {
                throw new IllegalStateException("Cannot clear a running flowgraph");
            }

            // Disconnect all connections first
            // This would require tracking connections

            // Remove all blocks
            for (Block block : new ArrayList<>(blocks)) {
                removeBlock(block);
            }
            blocks.clear();
        }
    }

    /**
     * Close the flowgraph and release native resources.
     * This method is idempotent and thread-safe.
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (closed.getAndSet(true)) {
                return; // Already closed
            }

            // Stop if running
            if (started.get()) {
                nativeStop(nativeHandle);
                started.set(false);
                notifyStopped();
            }

            // Destroy native object
            if (nativeHandle != 0) {
                nativeDestroyFlowgraph(nativeHandle);
                nativeHandle = 0;
            }

            // Clear Java references
            blocks.clear();
            listener = null;
        }
    }

    private void checkNotClosed() {
        if (closed.get()) {
            notifyError("Flowgraph is closed");
            throw new IllegalStateException("Flowgraph is closed");
        }
    }

    private void notifyError(String message) {
        Flowgraph.FlowgraphListener listener = this.listener;
        if (listener != null) {
            try {
                listener.onFlowgraphError(this, message);
            } catch (Exception e) {
                Log.e(TAG, "Error in onError callback", e);
            }
        }
    }

    private void notifyStarted() {
        Flowgraph.FlowgraphListener listener = this.listener;
        if (listener != null) {
            try {
                listener.onFlowgraphStarted(this);
            } catch (Exception e) {
                Log.e(TAG, "Error in onFlowgraphStarted callback", e);
            }
        }
    }

    private void notifyStopped() {
        Flowgraph.FlowgraphListener listener = this.listener;
        if (listener != null) {
            try {
                listener.onFlowgraphStopped(this);
            } catch (Exception e) {
                Log.e(TAG, "Error in onFlowgraphStopped callback", e);
            }
        }
    }

    // Native methods
    private native long nativeCreateFlowgraph(String name);
    private native void nativeDestroyFlowgraph(long handle);
    private native boolean nativeStart(long handle);
    private native void nativeStop(long handle);
    private native void nativeWaitForCompletion(long handle);
    private native void nativeRun(long handle);
    private native boolean nativeIsRunning(long handle);
    private native boolean nativeConnect(long handle,
                                         long srcBlock, String srcPort,
                                         long dstBlock, String dstPort);
    private native void nativeDisconnect(long handle,
                                         long srcBlock, String srcPort,
                                         long dstBlock, String dstPort);
    private native boolean nativeAreConnected(long handle,
                                              long srcBlock, String srcPort,
                                              long dstBlock, String dstPort);
    private native void nativeAddBlock(long handle, long blockHandle);
    private native void nativeRemoveBlock(long handle, long blockHandle);
}