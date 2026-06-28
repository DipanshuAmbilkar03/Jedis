package com.miniredis.memory;

import com.miniredis.store.DataType;

/**
 * OffHeapPointer — represents a pointer to direct memory allocation details and metadata.
 */
public class OffHeapPointer {

    private final int offset;
    private final int length;
    private final DataType type;
    private final long lastAccessTime;
    private final int accessFrequency;

    public OffHeapPointer(int offset, int length, DataType type, long lastAccessTime, int accessFrequency) {
        this.offset = offset;
        this.length = length;
        this.type = type;
        this.lastAccessTime = lastAccessTime;
        this.accessFrequency = accessFrequency;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public DataType getType() {
        return type;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public int getAccessFrequency() {
        return accessFrequency;
    }

    public long getIdleTime() {
        return System.currentTimeMillis() - lastAccessTime;
    }
}
