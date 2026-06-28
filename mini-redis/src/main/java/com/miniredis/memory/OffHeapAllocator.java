package com.miniredis.memory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * OffHeapAllocator — manages pre-allocated direct ByteBuffer blocks (slabs).
 * Implements a custom First-Fit memory allocator with block splitting and coalescing.
 */
public class OffHeapAllocator {

    private static class Block {
        int offset;
        int size;
        boolean free;

        Block(int offset, int size, boolean free) {
            this.offset = offset;
            this.size = size;
            this.free = free;
        }
    }

    private final ByteBuffer globalBuffer;
    private final List<Block> blocks;

    public OffHeapAllocator(int capacity) {
        this.globalBuffer = ByteBuffer.allocateDirect(capacity);
        this.blocks = new ArrayList<>();
        this.blocks.add(new Block(0, capacity, true));
    }

    /**
     * Allocate a contiguous region of off-heap memory.
     * Returns the offset of the allocated region.
     */
    public synchronized int allocate(int size) {
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.free && b.size >= size) {
                if (b.size > size) {
                    Block next = new Block(b.offset + size, b.size - size, true);
                    blocks.add(i + 1, next);
                }
                b.size = size;
                b.free = false;
                return b.offset;
            }
        }
        throw new OutOfMemoryError("Off-heap database memory exhausted!");
    }

    /**
     * Free the allocated region starting at the specified offset.
     */
    public synchronized void free(int offset) {
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.offset == offset) {
                b.free = true;
                coalesce();
                return;
            }
        }
    }

    /**
     * Write on-heap bytes into off-heap memory.
     */
    public synchronized void write(int offset, byte[] data) {
        globalBuffer.position(offset);
        globalBuffer.put(data);
    }

    /**
     * Read off-heap bytes into an on-heap buffer.
     */
    public synchronized void read(int offset, byte[] dest) {
        globalBuffer.position(offset);
        globalBuffer.get(dest);
    }

    private void coalesce() {
        for (int i = 0; i < blocks.size() - 1; i++) {
            Block current = blocks.get(i);
            Block next = blocks.get(i + 1);
            if (current.free && next.free) {
                current.size += next.size;
                blocks.remove(i + 1);
                i--; // Check again with updated index
            }
        }
    }

    /**
     * Clear all allocations.
     */
    public synchronized void clear() {
        int capacity = globalBuffer.capacity();
        blocks.clear();
        blocks.add(new Block(0, capacity, true));
    }
}
