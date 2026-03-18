package com.nori.tc.comm.core.buffer;

import com.nori.tc.comm.gateway.action.FrameBuffer;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;

/**
 * Reassembly buffer used by the sequential EQP processor.
 *
 * <p>This buffer accumulates raw bytes from inbound chunks and exposes
 * random-access read/copy/discard operations used by protocol frame extractors.
 * It also tracks per-byte trace provenance so that the parser can reuse the
 * enqueue-time traceId instead of generating a second traceId.</p>
 *
 * <p>Design constraints:</p>
 * <p>1) No dependency on Netty-specific buffer types</p>
 * <p>2) Bounded memory with explicit maxBytes guard</p>
 * <p>3) Deterministic provenance behavior across append/discard/clear</p>
 */
public final class ReassemblyBuffer implements FrameBuffer {

    /**
     * Internal storage.
     * - head: first readable byte index
     * - tail: one-past-last readable byte index
     */
    private byte[] buffer;
    private int head;
    private int tail;

    /**
     * Hard upper memory bound for the accumulated readable bytes.
     */
    private final int maxBytes;

    /**
     * Trace provenance segments aligned with readable bytes.
     *
     * <p>Each segment length is measured in bytes and segment order always
     * matches current readable byte order in this buffer.</p>
     */
    private final Deque<TraceSegment> traceSegments;

    /**
     * TraceId of the last byte consumed by the latest discard operation.
     *
     * <p>Frame extractors can read this value immediately after they discard
     * an extracted frame to bind the parsed message traceId.</p>
     */
    private String lastDiscardedTraceId;

    /**
     * Creates the reassembly buffer.
     *
     * @param initialCapacity initial backing array size (> 0)
     * @param maxBytes maximum readable bytes allowed (> 0)
     */
    public ReassemblyBuffer(final int initialCapacity, final int maxBytes) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be > 0");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be > 0");
        }
        if (initialCapacity > maxBytes) {
            throw new IllegalArgumentException("initialCapacity must be <= maxBytes");
        }
        this.buffer = new byte[initialCapacity];
        this.maxBytes = maxBytes;
        this.head = 0;
        this.tail = 0;
        this.traceSegments = new ArrayDeque<>();
        this.lastDiscardedTraceId = null;
    }

    /**
     * Returns current readable bytes.
     */
    public int readableBytes() {
        return tail - head;
    }

    /**
     * Returns configured maximum readable bytes.
     */
    public int maxBytes() {
        return maxBytes;
    }

    /**
     * Appends a chunk without trace provenance metadata.
     *
     * <p>This method remains for backward compatibility paths that do not
     * require per-frame trace propagation.</p>
     *
     * @param chunk chunk bytes
     */
    public void append(final byte[] chunk) {
        appendInternal(chunk, null, false);
    }

    /**
     * Appends a chunk with trace provenance metadata.
     *
     * <p>The provided traceId is recorded as the provenance for every appended
     * byte so that later discard operations can resolve the traceId of the last
     * consumed byte range.</p>
     *
     * @param chunk chunk bytes
     * @param traceId traceId created at enqueue time
     */
    public void append(final byte[] chunk, final String traceId) {
        appendInternal(chunk, traceId, true);
    }

    /**
     * Returns traceId of the most recent discard operation.
     *
     * <p>If no discard happened yet, or the last discarded bytes were appended
     * through a path that does not provide provenance, this method returns null.</p>
     */
    public String lastDiscardedTraceId() {
        return lastDiscardedTraceId;
    }

    /**
     * Reads one byte at relative offset without consuming.
     *
     * @param offset zero-based offset from current head
     * @return byte value
     */
    public byte get(final int offset) {
        final int idx = head + offset;
        if (offset < 0 || idx >= tail) {
            throw new IndexOutOfBoundsException("offset=" + offset + ", readableBytes=" + readableBytes());
        }
        return buffer[idx];
    }

    /**
     * Copies bytes from current readable area without consuming.
     *
     * @param offset zero-based offset from current head
     * @param length number of bytes to copy
     * @return copied bytes
     */
    public byte[] copy(final int offset, final int length) {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("offset/length must be >= 0");
        }
        if (offset + length > readableBytes()) {
            throw new IndexOutOfBoundsException("offset+length > readableBytes");
        }
        final byte[] out = new byte[length];
        System.arraycopy(buffer, head + offset, out, 0, length);
        return out;
    }

    /**
     * Discards bytes from head.
     *
     * <p>In addition to byte consumption, this method advances provenance
     * segments and updates {@link #lastDiscardedTraceId()} to the traceId
     * associated with the last consumed byte.</p>
     *
     * @param length bytes to discard
     */
    public void discard(final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }
        if (length == 0) {
            return;
        }
        if (length > readableBytes()) {
            throw new IndexOutOfBoundsException("length > readableBytes");
        }

        consumeTraceSegments(length);
        head += length;
        compactIfNeeded();
    }

    /**
     * Clears all buffered data and provenance state.
     */
    public void clear() {
        head = 0;
        tail = 0;
        traceSegments.clear();
        lastDiscardedTraceId = null;
    }

    /**
     * Shared append implementation.
     *
     * @param chunk chunk bytes
     * @param traceId candidate traceId
     * @param requireTrace whether traceId is mandatory
     */
    private void appendInternal(final byte[] chunk, final String traceId, final boolean requireTrace) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk is null");
        }
        if (chunk.length == 0) {
            return;
        }

        final String normalizedTraceId = normalizeTraceId(traceId, requireTrace);
        final int newSize = readableBytes() + chunk.length;
        if (newSize > maxBytes) {
            throw new IllegalStateException("ReassemblyBuffer overflow: " + newSize + " > " + maxBytes);
        }

        ensureWritable(chunk.length);
        System.arraycopy(chunk, 0, buffer, tail, chunk.length);
        tail += chunk.length;
        appendTraceSegment(chunk.length, normalizedTraceId);
    }

    /**
     * Normalizes traceId according to the selected contract.
     *
     * @param traceId candidate traceId
     * @param requireTrace true when caller requires non-empty traceId
     * @return normalized traceId or null when optional path has no value
     */
    private static String normalizeTraceId(final String traceId, final boolean requireTrace) {
        if (traceId == null) {
            if (requireTrace) {
                throw new IllegalArgumentException("traceId is required");
            }
            return null;
        }
        final String normalized = traceId.trim();
        if (normalized.isEmpty()) {
            if (requireTrace) {
                throw new IllegalArgumentException("traceId is required");
            }
            return null;
        }
        return normalized;
    }

    /**
     * Adds one provenance segment and merges with previous segment when possible.
     *
     * @param length segment length in bytes
     * @param traceId segment traceId
     */
    private void appendTraceSegment(final int length, final String traceId) {
        if (length <= 0) {
            return;
        }
        final TraceSegment tailSegment = traceSegments.peekLast();
        if (tailSegment != null && Objects.equals(tailSegment.traceId, traceId)) {
            tailSegment.length += length;
            return;
        }
        traceSegments.addLast(new TraceSegment(length, traceId));
    }

    /**
     * Consumes provenance segments according to discarded byte length.
     *
     * @param length bytes consumed by discard
     */
    private void consumeTraceSegments(final int length) {
        int remaining = length;
        String consumedTraceId = null;

        while (remaining > 0) {
            final TraceSegment current = traceSegments.peekFirst();
            if (current == null) {
                throw new IllegalStateException(
                        "Trace provenance underflow while discarding. requested=" + length + ", remaining=" + remaining
                );
            }

            if (current.length <= remaining) {
                remaining -= current.length;
                consumedTraceId = current.traceId;
                traceSegments.pollFirst();
            } else {
                current.length -= remaining;
                consumedTraceId = current.traceId;
                remaining = 0;
            }
        }

        lastDiscardedTraceId = consumedTraceId;
    }

    /**
     * Ensures there is enough writable capacity for additional bytes.
     *
     * @param additionalBytes number of bytes that will be appended
     */
    private void ensureWritable(final int additionalBytes) {
        compactIfNeeded();

        int required = tail + additionalBytes;
        if (required > buffer.length && head > 0) {
            compact();
            required = tail + additionalBytes;
        }
        if (required <= buffer.length) {
            return;
        }

        final int currentSize = readableBytes();
        int newCapacity = buffer.length;

        while (newCapacity < required) {
            newCapacity *= 2;
            if (newCapacity > maxBytes) {
                newCapacity = maxBytes;
                break;
            }
        }
        if (newCapacity < required) {
            throw new IllegalStateException("Cannot grow buffer beyond maxBytes=" + maxBytes);
        }

        final byte[] newBuf = Arrays.copyOfRange(buffer, head, head + currentSize);
        buffer = new byte[newCapacity];
        System.arraycopy(newBuf, 0, buffer, 0, currentSize);
        head = 0;
        tail = currentSize;
    }

    /**
     * Compacts the backing array when head offset grows enough.
     */
    private void compactIfNeeded() {
        if (head == 0) {
            return;
        }

        final int currentSize = readableBytes();
        final boolean shouldCompact = head > (buffer.length / 2) || tail == buffer.length;
        if (!shouldCompact) {
            return;
        }

        if (currentSize == 0) {
            head = 0;
            tail = 0;
            return;
        }
        compact();
    }

    /**
     * Moves readable bytes to the beginning of the backing array.
     */
    private void compact() {
        final int currentSize = readableBytes();
        if (currentSize == 0) {
            head = 0;
            tail = 0;
            return;
        }

        System.arraycopy(buffer, head, buffer, 0, currentSize);
        head = 0;
        tail = currentSize;
    }

    /**
     * Internal mutable provenance segment.
     */
    private static final class TraceSegment {
        private int length;
        private final String traceId;

        private TraceSegment(final int length, final String traceId) {
            this.length = length;
            this.traceId = traceId;
        }
    }
}
