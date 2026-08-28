package com.github.alibehrozi.wave.microdsp.blocks.sources;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * Reads data from a file and outputs it to the DSP pipeline.
 */
public class FileSource extends Block {

    private final DataType dataType;
    private final String filename;
    private final boolean repeat;

    /**
     * Create a new FileSource.
     * @param dataType Data type to output
     * @param filename Path to the input file
     * @param repeat   Whether to loop the file playback when EOF is reached
     * @param name     Block name
     */
    public FileSource(@NonNull DataType dataType, @NonNull String filename, boolean repeat, @NonNull String name) {
        super(name, nativeCreateFileSource(dataType.ordinal(), filename, repeat, name));
        this.dataType = dataType;
        this.filename = filename;
        this.repeat = repeat;
    }

    /**
     * Create a new FileSource with default name.
     * @param dataType Data type to output
     * @param filename Path to the input file
     * @param repeat   Whether to loop the file playback when EOF is reached
     */
    public FileSource(@NonNull DataType dataType, @NonNull String filename, boolean repeat) {
        this(dataType, filename, repeat, "file_source");
    }

    /**
     * Get the data type produced by this file source.
     * @return Output data type
     */
    public DataType getDataType() {
        return dataType;
    }

    /**
     * Get the path to the input file.
     * @return File path
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Check if the file source is configured to repeat upon EOF.
     * @return true if repeat is enabled, false otherwise
     */
    public boolean isRepeat() {
        return repeat;
    }

    /**
     * Seek to a byte offset within the input file.
     * @param position Target byte offset position
     */
    public void seek(long position) {
        nativeSeek(nativeHandle, position);
    }

    /**
     * Get the total size of the input file in bytes.
     * @return File size in bytes
     */
    public long getFileSize() {
        return nativeGetFileSize(nativeHandle);
    }

    /**
     * Get the current read position in bytes within the file.
     * @return Current read position in bytes
     */
    public long getPosition() {
        return nativeGetPosition(nativeHandle);
    }

    private static native long nativeCreateFileSource(int dataType, String filename, boolean repeat, String name);
    private native void nativeSeek(long handle, long position);
    private native long nativeGetFileSize(long handle);
    private native long nativeGetPosition(long handle);
}
