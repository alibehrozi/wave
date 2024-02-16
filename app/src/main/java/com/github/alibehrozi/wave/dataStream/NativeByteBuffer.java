package com.github.alibehrozi.wave.dataStream;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

public class NativeByteBuffer extends AbstractSerializedData {

    protected long address;
    public ByteBuffer buffer;
    private boolean justCalc;
    private int len;
    public boolean reused = true;

    private static final ThreadLocal<LinkedList<NativeByteBuffer>> addressWrappers =
            ThreadLocal.withInitial(LinkedList::new);

    public static NativeByteBuffer wrap(long address) {
        if (address != 0) {
            LinkedList<NativeByteBuffer> queue = addressWrappers.get();
            NativeByteBuffer result = queue.poll();
            if (result == null) {
                result = new NativeByteBuffer(0, true);
            }
            result.address = address;
            result.reused = false;
            result.buffer = getJavaByteBuffer(address);
            result.buffer.limit((int) limit(address));
            long position = position(address);
            if (position <= result.buffer.limit()) {
                result.buffer.position((int) position);
            }
            result.buffer.order(ByteOrder.LITTLE_ENDIAN);
            return result;
        } else {
            return null;
        }
    }

    private NativeByteBuffer(int address, boolean wrap) {

    }

    public NativeByteBuffer(int size) throws Exception {
        if (size >= 0) {
            address = getFreeBuffer(size);
            if (address != 0) {
                buffer = getJavaByteBuffer(address);
                buffer.position(0);
                buffer.limit(size);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }
        } else {
            throw new Exception("invalid NativeByteBuffer size");
        }
    }

    public NativeByteBuffer(boolean calculate) {
        justCalc = calculate;
    }

    public int position() {
        return buffer.position();
    }

    public void position(int position) {
        buffer.position(position);
    }

    public int capacity() {
        return buffer.capacity();
    }

    public int limit() {
        return buffer.limit();
    }

    public void limit(int limit) {
        buffer.limit(limit);
    }

    public void put(ByteBuffer buff) {
        buffer.put(buff);
    }

    public void rewind() {
        if (justCalc) {
            len = 0;
        } else {
            buffer.rewind();
        }
    }

    public void compact() {
        buffer.compact();
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    public void writeInt32(int x) {
        try {
            if (!justCalc) {
                buffer.putInt(x);
            } else {
                len += 4;
            }
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write int32 error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeInt64(long x) {
        try {
            if (!justCalc) {
                buffer.putLong(x);
            } else {
                len += 8;
            }
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write int64 error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeFloat(float f) {
        try {
            if (!justCalc) {
                buffer.putInt(Float.floatToIntBits(f));
            } else {
                len += 4;
            }
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write float error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeBool(boolean value) {
        if (!justCalc) {
            if (value) {
                writeInt32(0x997275b5);
            } else {
                writeInt32(0xbc799737);
            }
        } else {
            len += 4;
        }
    }

    public void writeBytes(byte[] b) {
        try {
            if (!justCalc) {
                buffer.put(b);
            } else {
                len += b.length;
            }
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write raw error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeBytes(byte[] b, int offset, int count) {
        try {
            if (!justCalc) {
                buffer.put(b, offset, count);
            } else {
                len += count;
            }
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write raw error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeByte(int i) {
        writeByte((byte) i);
    }

    public void writeByte(byte b) {
        try {
            if (!justCalc) {
                buffer.put(b);
            } else {
                len += 1;
            }
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write byte error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeString(String s) {
        if (s == null) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write string null");
                Log.e(TAG, "new Throwable()");
            }
            s = "";
        }
        try {
            writeByteArray(s.getBytes("UTF-8"));
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write string error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeByteArray(byte[] b, int offset, int count) {
        try {
            if (count <= 253) {
                if (!justCalc) {
                    buffer.put((byte) count);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    buffer.put((byte) 254);
                    buffer.put((byte) count);
                    buffer.put((byte) (count >> 8));
                    buffer.put((byte) (count >> 16));
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                buffer.put(b, offset, count);
            } else {
                len += count;
            }
            int i = count <= 253 ? 1 : 4;
            while ((count + i) % 4 != 0) {
                if (!justCalc) {
                    buffer.put((byte) 0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write byte array error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeByteArray(byte[] b) {
        try {
            if (b.length <= 253) {
                if (!justCalc) {
                    buffer.put((byte) b.length);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    buffer.put((byte) 254);
                    buffer.put((byte) b.length);
                    buffer.put((byte) (b.length >> 8));
                    buffer.put((byte) (b.length >> 16));
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                buffer.put(b);
            } else {
                len += b.length;
            }
            int i = b.length <= 253 ? 1 : 4;
            while ((b.length + i) % 4 != 0) {
                if (!justCalc) {
                    buffer.put((byte) 0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write byte array error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeDouble(double d) {
        try {
            writeInt64(Double.doubleToRawLongBits(d));
        } catch (Exception e) {
            if (LOGS_ENABLED) {
                Log.e(TAG, "write double error");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void writeByteBuffer(NativeByteBuffer b) {
        try {
            int l = b.limit();
            if (l <= 253) {
                if (!justCalc) {
                    buffer.put((byte) l);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    buffer.put((byte) 254);
                    buffer.put((byte) l);
                    buffer.put((byte) (l >> 8));
                    buffer.put((byte) (l >> 16));
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                b.rewind();
                buffer.put(b.buffer);
            } else {
                len += l;
            }
            int i = l <= 253 ? 1 : 4;
            while ((l + i) % 4 != 0) {
                if (!justCalc) {
                    buffer.put((byte) 0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void writeBytes(NativeByteBuffer b) {
        if (justCalc) {
            len += b.limit();
        } else {
            b.rewind();
            buffer.put(b.buffer);
        }
    }

    public int getIntFromByte(byte b) {
        return b >= 0 ? b : ((int) b) + 256;
    }

    public int length() {
        if (!justCalc) {
            return buffer.position();
        }
        return len;
    }

    public void skip(int count) {
        if (count == 0) {
            return;
        }
        if (!justCalc) {
            buffer.position(buffer.position() + count);
        } else {
            len += count;
        }
    }

    public int getPosition() {
        return buffer.position();
    }

    public byte readByte(boolean exception) {
        try {
            return buffer.get();
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read byte error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read byte error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return 0;
    }

    public int readInt32(boolean exception) {
        try {
            return buffer.getInt();
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read int32 error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read int32 error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return 0;
    }

    public float readFloat(boolean exception) {
        try {
            return Float.intBitsToFloat(buffer.getInt());
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read float error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read float error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return 0;
    }

    public boolean readBool(boolean exception) {
        int consructor = readInt32(exception);
        if (consructor == 0x997275b5) {
            return true;
        } else if (consructor == 0xbc799737) {
            return false;
        }
        if (exception) {
            throw new RuntimeException("Not bool value!");
        } else {
            if (LOGS_ENABLED) {
                Log.e(TAG, "Not bool value!");
            }
        }
        return false;
    }

    public long readInt64(boolean exception) {
        try {
            return buffer.getLong();
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read int64 error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read int64 error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return 0;
    }

    public void readBytes(byte[] b, boolean exception) {
        try {
            buffer.get(b);
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read raw error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read raw error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    public void readBytes(byte[] b, int offset, int count, boolean exception) {
        try {
            buffer.get(b, offset, count);
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read raw error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read raw error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    public byte[] readData(int count, boolean exception) {
        byte[] arr = new byte[count];
        readBytes(arr, exception);
        return arr;
    }

    public String readString(boolean exception) {
        int startReadPosition = getPosition();
        try {
            int sl = 1;
            int l = getIntFromByte(buffer.get());
            if (l >= 254) {
                l = getIntFromByte(buffer.get()) | (getIntFromByte(buffer.get()) << 8) | (getIntFromByte(buffer.get()) << 16);
                sl = 4;
            }
            byte[] b = new byte[l];
            buffer.get(b);
            int i = sl;
            while ((l + i) % 4 != 0) {
                buffer.get();
                i++;
            }
            return new String(b, "UTF-8");
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read string error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read string error");
                    Log.e(TAG, e.getMessage());
                }
            }
            position(startReadPosition);
        }
        return "";
    }

    public byte[] readByteArray(boolean exception) {
        try {
            int sl = 1;
            int l = getIntFromByte(buffer.get());
            if (l >= 254) {
                l = getIntFromByte(buffer.get()) | (getIntFromByte(buffer.get()) << 8) | (getIntFromByte(buffer.get()) << 16);
                sl = 4;
            }
            byte[] b = new byte[l];
            buffer.get(b);
            int i = sl;
            while ((l + i) % 4 != 0) {
                buffer.get();
                i++;
            }
            return b;
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read byte array error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read byte array error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return new byte[0];
    }

    public NativeByteBuffer readByteBuffer(boolean exception) {
        try {
            int sl = 1;
            int l = getIntFromByte(buffer.get());
            if (l >= 254) {
                l = getIntFromByte(buffer.get()) | (getIntFromByte(buffer.get()) << 8) | (getIntFromByte(buffer.get()) << 16);
                sl = 4;
            }
            NativeByteBuffer b = new NativeByteBuffer(l);
            int old = buffer.limit();
            buffer.limit(buffer.position() + l);
            b.buffer.put(buffer);
            buffer.limit(old);
            b.buffer.position(0);
            int i = sl;
            while ((l + i) % 4 != 0) {
                buffer.get();
                i++;
            }
            return b;
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read byte array error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read byte array error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return null;
    }

    public double readDouble(boolean exception) {
        try {
            return Double.longBitsToDouble(readInt64(exception));
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read double error", e);
            } else {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "read double error");
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return 0;
    }

    public void reuse() {
        if (address != 0) {
            addressWrappers.get().add(this);
            reused = true;
            reuse(address);
        }
    }

    @Override
    public int remaining() {
        return buffer.remaining();
    }

    @Override
    protected void finalize() throws Throwable {
        if (!reused) {
            reuse();
        }
        super.finalize();
    }

    static native long getFreeBuffer(int length);
    static native ByteBuffer getJavaByteBuffer(long address);
    static native long limit(long address);
    static native long position(long address);
    static native void reuse(long address);
    static native void setJava(boolean useJavaByteBuffers);

}