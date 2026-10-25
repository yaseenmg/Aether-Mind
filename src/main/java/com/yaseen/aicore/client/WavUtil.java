package com.yaseen.aicore.client;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WavUtil {
    public static byte[] wrapPcm16Mono(byte[] pcm, int sampleRate) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
            out.write("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            writeLE32(out, 36 + pcm.length);
            out.write("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            writeLE32(out, 16);
            writeLE16(out, 1);
            writeLE16(out, 1);
            writeLE32(out, sampleRate);
            writeLE32(out, sampleRate * 2);
            writeLE16(out, 2);
            writeLE16(out, 16);
            out.write("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            writeLE32(out, pcm.length);
            out.write(pcm);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
    private static void writeLE16(ByteArrayOutputStream o, int v) { o.write(v & 255); o.write((v >>> 8) & 255); }
    private static void writeLE32(ByteArrayOutputStream o, int v) { o.write(v & 255); o.write((v >>> 8) & 255); o.write((v >>> 16) & 255); o.write((v >>> 24) & 255); }
    private WavUtil() {}
}
