package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * UNBOUND 상태 전용 inbox.
 *
 * 설계 의도
 * - Netty channelRead에서는 "byte[] enqueue"만 수행합니다.
 * - 실제 buffer append/parse는 별도 스레드(bind executor)에서 수행합니다.
 * - UNBOUND 단계에서 메모리 폭주를 막기 위해 총 누적 바이트를 제한합니다.
 */
public final class UnboundInbox {

    private final int maxBytes;
    private final Deque<byte[]> chunks = new ArrayDeque<>();
    private final ReassemblyBuffer reassemblyBuffer;

    private int queuedBytes = 0;

    public UnboundInbox(final int initialBytes, final int maxBytes) {
        if (initialBytes <= 0) {
            throw new IllegalArgumentException("initialBytes must be > 0");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be > 0");
        }
        if (initialBytes > maxBytes) {
            throw new IllegalArgumentException("initialBytes must be <= maxBytes");
        }
        this.maxBytes = maxBytes;
        this.reassemblyBuffer = new ReassemblyBuffer(initialBytes, maxBytes);
    }

    /**
     * Netty IO 스레드에서 호출.
     * - enqueue만 수행
     *
     * @return true if accepted, false if overflow
     */
    public synchronized boolean offer(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return true;
        }
        if (queuedBytes + bytes.length > maxBytes) {
            return false;
        }
        chunks.addLast(bytes);
        queuedBytes += bytes.length;
        return true;
    }

    /**
     * Bind executor에서 호출.
     *
     * - 큐에 쌓인 바이트들을 ReassemblyBuffer로 이동합니다.
     * - ReassemblyBuffer overflow는 예외를 던집니다.
     */
    public synchronized void drainToBuffer() {
        while (!chunks.isEmpty()) {
            final byte[] bytes = chunks.pollFirst();
            if (bytes == null || bytes.length == 0) {
                continue;
            }
            queuedBytes -= bytes.length;
            reassemblyBuffer.append(bytes);
        }
    }

    public ReassemblyBuffer buffer() {
        return reassemblyBuffer;
    }

    /**
     * 등록 완료 후 UNBOUND 단계 버퍼를 비웁니다.
     */
    public synchronized void clear() {
        chunks.clear();
        queuedBytes = 0;
        reassemblyBuffer.clear();
    }
}
