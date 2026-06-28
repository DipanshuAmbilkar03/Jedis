package com.miniredis.cluster;

import java.nio.charset.StandardCharsets;

/**
 * CRC16 — pure Java implementation of Redis CRC16 hashing with hash-tags support.
 * Maps keys to one of the 16384 cluster sharding slots (0-16383).
 */
public class CRC16 {

    /**
     * Compute slot index for a given key, honoring Redis hash tags (e.g., "user:{123}:profile").
     */
    public static int getSlot(String key) {
        int start = key.indexOf('{');
        if (start != -1) {
            int end = key.indexOf('}', start + 1);
            if (end != -1 && end > start + 1) {
                key = key.substring(start + 1, end);
            }
        }

        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        int crc = 0x0000;
        for (byte b : bytes) {
            crc = ((crc >>> 8) | (crc << 8)) & 0xffff;
            crc ^= (b & 0xff);
            crc ^= ((crc & 0xff) >> 4);
            crc ^= (crc << 12) & 0xffff;
            crc ^= ((crc & 0xff) << 5) & 0xffff;
        }
        return crc % 16384;
    }
}
