package com.searchengine.index;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteIOTest {

    @Test
    void roundTripsVarintsFixedWidthAndStrings() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long[] values = {0, 1, 127, 128, 300, 16_383, 16_384, Integer.MAX_VALUE, Long.MAX_VALUE};
        try (ByteIO.Out out = new ByteIO.Out(bytes)) {
            for (long v : values) {
                out.writeVLong(v);
            }
            out.writeInt(-7);
            out.writeLong(Long.MIN_VALUE);
            out.writeString("héllo 世界");
        }
        ByteIO.In in = new ByteIO.In(ByteBuffer.wrap(bytes.toByteArray()), 0);
        for (long v : values) {
            assertEquals(v, in.readVLong());
        }
        assertEquals(-7, in.readInt());
        assertEquals(Long.MIN_VALUE, in.readLong());
        assertEquals("héllo 世界", in.readString());
        assertEquals(bytes.size(), in.position());
    }

    @Test
    void smallNumbersTakeOneByte() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ByteIO.Out out = new ByteIO.Out(bytes)) {
            out.writeVInt(127);
            assertEquals(1, out.position());
            out.writeVInt(128);
            assertEquals(3, out.position());
        }
    }
}
