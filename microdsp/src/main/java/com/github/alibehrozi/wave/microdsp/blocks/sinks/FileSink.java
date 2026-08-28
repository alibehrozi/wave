package com.github.alibehrozi.wave.microdsp.blocks.sinks;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;
import com.github.alibehrozi.wave.microdsp.core.Port;

/**
 * File output sink block for writing data to files.
 * Supports all data types and various file modes including overwrite,
 * append, and timestamp-based file naming.
 *
 * <p>Example usage:
 * <pre>
 * FileSink sink = new FileSink(DataType.FLOAT, "output.raw");
 * sink.setMode(FileMode.TIMESTAMP);
 * sink.setMaxFileSize(100 * 1024 * 1024); // 100MB per file
 * sink.start();
 * </pre>
 */
public class FileSink extends Block {

    /**
     * File operation modes.
     * OVERWRITE - Overwrite existing file or create new
     * APPEND - Append to existing file
     * TIMESTAMP - Create new file with timestamp in name
     */
    public enum FileMode {
        OVERWRITE,
        APPEND,
        TIMESTAMP
    }

    private String filename;
    private FileMode mode;
    private long maxFileSize;

    /**
     * Create a new FileSink block.
     * @param dataType data type to write
     * @param filename output filename
     * @param mode     file operation mode
     * @param name     block name
     * @throws IllegalArgumentException if dataType is null, filename is null or empty, or name is null or empty
     */
    public FileSink(DataType dataType, String filename, FileMode mode, String name) {
        super(name, nativeCreateFileSink(
                validateDataType(dataType).ordinal(),
                validateFilename(filename),
                validateMode(mode).ordinal(),
                name));

        this.filename = filename;
        this.mode = mode;
        this.maxFileSize = 0;
    }

    private static DataType validateDataType(DataType type) {
        if (type == null) {
            throw new IllegalArgumentException("DataType cannot be null");
        }
        return type;
    }

    private static String validateFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        return filename;
    }

    private static FileMode validateMode(FileMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("FileMode cannot be null");
        }
        return mode;
    }

    /**
     * Create a new FileSink with default name.
     * @param dataType data type to write
     * @param filename output filename
     * @param mode     file operation mode
     */
    public FileSink(DataType dataType, String filename, FileMode mode) {
        this(dataType, filename, mode, "file_sink");
    }

    /**
     * Create a new FileSink with OVERWRITE mode.
     * @param dataType data type to write
     * @param filename output filename
     */
    public FileSink(DataType dataType, String filename) {
        this(dataType, filename, FileMode.OVERWRITE, "file_sink");
    }

    /**
     * Set output filename.
     * @param filename new filename (must not be null or empty)
     * @throws IllegalArgumentException if filename is null or empty
     * @throws IllegalStateException    if block is closed
     */
    public void setFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        checkNotClosed();
        this.filename = filename;
        nativeSetFilename(nativeHandle, filename);
    }

    /**
     * Get current filename.
     * @return current filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Set file operation mode.
     * @param mode file mode (must not be null)
     * @throws IllegalArgumentException if mode is null
     * @throws IllegalStateException    if block is closed
     */
    public void setMode(FileMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("FileMode cannot be null");
        }
        checkNotClosed();
        this.mode = mode;
        nativeSetMode(nativeHandle, mode.ordinal());
    }

    /**
     * Get current file mode.
     * @return current file mode
     */
    public FileMode getMode() {
        return mode;
    }

    /**
     * Set maximum file size before rotation.
     * When the file reaches this size, it will be rotated.
     * @param maxSize maximum file size in bytes (0 for unlimited)
     * @throws IllegalArgumentException if maxSize is negative
     * @throws IllegalStateException    if block is closed
     */
    public void setMaxFileSize(long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("Max file size cannot be negative");
        }
        checkNotClosed();
        this.maxFileSize = maxSize;
        nativeSetMaxFileSize(nativeHandle, maxSize);
    }

    /**
     * Get maximum file size.
     * @return maximum file size in bytes
     */
    public long getMaxFileSize() {
        return maxFileSize;
    }

    /**
     * Start recording data to file.
     * @throws IllegalStateException if block is closed
     */
    public void startRecording() {
        checkNotClosed();
        nativeStartRecording(nativeHandle);
    }

    /**
     * Stop recording data and close file.
     * Incoming data will be discarded (skipped) without stalling upstream blocks.
     * @throws IllegalStateException if block is closed
     */
    public void stopRecording() {
        checkNotClosed();
        nativeStopRecording(nativeHandle);
    }

    /**
     * Check if recording is currently active.
     * @return true if recording, false otherwise
     * @throws IllegalStateException if block is closed
     */
    public boolean isRecording() {
        checkNotClosed();
        return nativeIsRecording(nativeHandle);
    }

    /**
     * Set unbuffered I/O mode.
     * When enabled, data is written directly to disk without buffering.
     * @param unbuffered true for unbuffered writes
     * @throws IllegalStateException if block is closed
     */
    public void setUnbuffered(boolean unbuffered) {
        checkNotClosed();
        nativeSetUnbuffered(nativeHandle, unbuffered);
    }

    /**
     * Flush file buffers to disk.
     * @throws IllegalStateException if block is closed
     */
    public void flush() {
        checkNotClosed();
        nativeFlush(nativeHandle);
    }

    /**
     * Close the output file.
     * @throws IllegalStateException if block is closed
     */
    public void close() {
        checkNotClosed();
        nativeClose(nativeHandle);
    }

    /**
     * Reopen the file (useful after rotation).
     * @throws IllegalStateException if block is closed
     */
    public void reopen() {
        checkNotClosed();
        nativeReopen(nativeHandle);
    }

    /**
     * Get total bytes written to file.
     * @return bytes written
     * @throws IllegalStateException if block is closed
     */
    public long getBytesWritten() {
        checkNotClosed();
        return nativeGetBytesWritten(nativeHandle);
    }

    /**
     * Check if file is open.
     * @return true if file is open
     * @throws IllegalStateException if block is closed
     */
    public boolean isOpen() {
        checkNotClosed();
        return nativeIsOpen(nativeHandle);
    }

    /**
     * Create a Java Port wrapper for a native port.
     * @param portHandle native port pointer
     * @param direction  port direction
     * @param name       port name
     * @return Port wrapper
     */
    @Override
    protected Port createPortWrapper(long portHandle, Port.Direction direction, String name) {
        return new Port(portHandle, name, direction);
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("FileSink is closed");
        }
    }

    public boolean isClosed() {
        return nativeHandle == 0;
    }

    // Native methods
    private static native long nativeCreateFileSink(int dataType, String filename, int mode, String name);
    private native void nativeDestroyFileSink(long handle);
    private native void nativeSetFilename(long handle, String filename);
    private native void nativeSetMode(long handle, int mode);
    private native void nativeSetMaxFileSize(long handle, long maxSize);
    private native void nativeStartRecording(long handle);
    private native void nativeStopRecording(long handle);
    private native boolean nativeIsRecording(long handle);
    private native void nativeSetUnbuffered(long handle, boolean unbuffered);
    private native void nativeFlush(long handle);
    private native void nativeClose(long handle);
    private native void nativeReopen(long handle);
    private native long nativeGetBytesWritten(long handle);
    private native boolean nativeIsOpen(long handle);
    private native String nativeGetFilename(long handle);
}
