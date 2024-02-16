package com.github.alibehrozi.wave.dataStream;

import java.util.LinkedList;

public class ByteBufferStream {

    protected long address;

    private static final ThreadLocal<LinkedList<ByteBufferStream>> addressWrappers =
            ThreadLocal.withInitial(LinkedList::new);

    public static ByteBufferStream wrap(long address) {
        if (address != 0) {
            LinkedList<ByteBufferStream> queue = addressWrappers.get();
            ByteBufferStream result = queue.poll();
            if (result == null) {
                result = new ByteBufferStream();
            }
            result.address = address;
            return result;
        } else {
            return null;
        }
    }

    public void add(NativeByteBuffer buffer) {
        if (address != 0) {
            appendBytes(address, buffer.address);
        }
    }

    public void get(NativeByteBuffer buffer) {
        if (address != 0) {
            getBuffer(address, buffer.address);
        }
    }

    public NativeByteBuffer get() {
        if (address != 0) {
            return NativeByteBuffer.wrap(
                    getData(address)
            );
        }
        return null;
    }

    public void discard(int count) {
        if (address != 0) {
            discardBytes(address, count);
        }
    }

    public void remove() {
        if (address != 0) {
            addressWrappers.get().add(this);
            remove(address);
        }
    }

    static native void appendBytes(long streamAddress, long bufferAddress);
    static native boolean hasData(long streamAddress);
    static native void getBuffer(long streamAddress, long bufferAddress);
    static native long getData(long streamAddress);
    static native void discardBytes(long streamAddress, int count);
    static native void remove(long streamAddress);
}
