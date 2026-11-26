package com.searchengine.index;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Variable-length integer codec used by segment files. A varint stores 7 bits per byte, low
 * bits first, with the high bit set on every byte except the last — so small numbers (doc-id
 * gaps, frequencies, position gaps) take one byte.
 */
public final class ByteIO {
    private ByteIO() {
    }

    /** Buffered output that tracks its absolute position. */
    public static final class Out implements AutoCloseable {
        private final OutputStream out;
        private long position;

        public Out(OutputStream out) {
            this.out = new BufferedOutputStream(out, 1 << 16);
        }

        public long position() {
            return position;
        }

        public void writeByte(int b) throws IOException {
            out.write(b);
            position++;
        }

        public void writeInt(int v) throws IOException {
            writeByte(v >>> 24);
            writeByte(v >>> 16);
            writeByte(v >>> 8);
            writeByte(v);
        }

        public void writeLong(long v) throws IOException {
            writeInt((int) (v >>> 32));
            writeInt((int) v);
        }

        public void writeVInt(int v) throws IOException {
            writeVLong(v & 0xFFFFFFFFL);
        }

        public void writeVLong(long v) throws IOException {
            if (v < 0) {
                throw new IllegalArgumentException("negative vlong " + v);
            }
            while ((v & ~0x7FL) != 0) {
                writeByte((int) ((v & 0x7F) | 0x80));
                v >>>= 7;
            }
            writeByte((int) v);
        }

        public void writeBytes(byte[] bytes) throws IOException {
            out.write(bytes);
            position += bytes.length;
        }

        public void writeString(String s) throws IOException {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            writeVInt(bytes.length);
            out.write(bytes);
            position += bytes.length;
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    /** Cursor over a (possibly memory-mapped) buffer; absolute positioning, not thread-safe. */
    public static final class In {
        private final ByteBuffer buf;
        private int pos;

        public In(ByteBuffer buf, long position) {
            this.buf = buf;
            this.pos = Math.toIntExact(position);
        }

        public long position() {
            return pos;
        }

        public void seek(long position) {
            pos = Math.toIntExact(position);
        }

        public int readByte() {
            return buf.get(pos++) & 0xFF;
        }

        public int readInt() {
            int v = buf.getInt(pos);
            pos += 4;
            return v;
        }

        public long readLong() {
            long v = buf.getLong(pos);
            pos += 8;
            return v;
        }

        public int readVInt() {
            return Math.toIntExact(readVLong());
        }

        public long readVLong() {
            long v = 0;
            int shift = 0;
            while (true) {
                int b = readByte();
                v |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return v;
                }
                shift += 7;
            }
        }

        public String readString() {
            int len = readVInt();
            byte[] bytes = new byte[len];
            buf.get(pos, bytes);
            pos += len;
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
