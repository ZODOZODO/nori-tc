package com.nori.tc.comm.core.buffer;

import java.util.Arrays;

/**
 * eqp별 수신 바이트 재조립(Reassembly) 버퍼
 *
 * 배경
 * - Netty channelRead에서는 파싱/프레이밍을 하지 않고 raw bytes chunk를 eqp별 큐에 적재만 합니다.
 * - 이후 eqp별 순차 처리 루프에서 chunk를 하나씩 꺼내어 버퍼에 누적한 후,
 *   HSMS/SOCKET 프레이밍(프레임 추출) 로직이 "완전한 프레임"이 될 때까지 버퍼를 읽습니다.
 *
 * 설계 원칙
 * - core 엔진 레벨에서 동작하는 범용 버퍼로, Netty(ByteBuf) 등에 의존하지 않습니다.
 * - read(프레임 추출)가 완료되면 consumed 바이트를 discard 하여 메모리 폭발을 방지합니다.
 *
 * 성능 참고
 * - 매우 고성능이 필요하면 ring-buffer/DirectByteBuffer 등으로 대체할 수 있습니다.
 * - 현재 구현은 "가독성/안정성"을 우선합니다.
 */
public final class ReassemblyBuffer {

    /**
     * 내부 저장 배열
     * - head: 읽기 시작 위치
     * - tail: 쓰기 끝 위치(미포함)
     */
    private byte[] buffer;
    private int head;
    private int tail;

    /**
     * 안전 상한(이 값을 초과하면 더 이상 append를 허용하지 않는 정책을 권장)
     * - 폭주/비정상 입력으로 인한 OOM을 예방하기 위한 상한입니다.
     */
    private final int maxBytes;

    /**
     * @param initialCapacity 초기 버퍼 크기(최소 1)
     * @param maxBytes       버퍼에 누적 가능한 최대 바이트(최소 1)
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
    }

    /**
     * 현재 읽을 수 있는 바이트 수
     */
    public int readableBytes() {
        return tail - head;
    }

    /**
     * 내부 상한
     */
    public int maxBytes() {
        return maxBytes;
    }

    /**
     * 버퍼에 raw chunk를 추가합니다.
     *
     * @param chunk 추가할 바이트(Null 불가)
     * @throws IllegalStateException 누적 크기가 maxBytes를 초과하는 경우
     */
    public void append(final byte[] chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk is null");
        }
        if (chunk.length == 0) {
            return;
        }

        // 현재 누적 크기 + 추가 크기가 상한을 넘는지 1차 방어
        final int newSize = readableBytes() + chunk.length;
        if (newSize > maxBytes) {
            throw new IllegalStateException("ReassemblyBuffer overflow: " + newSize + " > " + maxBytes);
        }

        ensureWritable(chunk.length);
        System.arraycopy(chunk, 0, buffer, tail, chunk.length);
        tail += chunk.length;
    }

    /**
     * head 기준 offset 위치의 바이트를 읽습니다(소비하지 않음).
     *
     * @param offset head 기준 0부터
     */
    public byte get(final int offset) {
        final int idx = head + offset;
        if (offset < 0 || idx >= tail) {
            throw new IndexOutOfBoundsException("offset=" + offset + ", readableBytes=" + readableBytes());
        }
        return buffer[idx];
    }

    /**
     * head 기준 offset부터 length만큼을 복사하여 반환합니다(소비하지 않음).
     *
     * @param offset head 기준 0부터
     * @param length 읽을 길이
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
     * 앞에서부터 length 바이트를 소비(discard)합니다.
     * - 프레임 추출이 완료되어 해당 구간이 더 이상 필요 없을 때 호출합니다.
     *
     * @param length 소비할 길이
     */
    public void discard(final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }
        if (length == 0) return;

        if (length > readableBytes()) {
            throw new IndexOutOfBoundsException("length > readableBytes");
        }

        head += length;
        compactIfNeeded();
    }

    /**
     * 버퍼를 비웁니다.
     * - eqp quarantine/재연결 등으로 상태 초기화가 필요할 때 사용
     */
    public void clear() {
        head = 0;
        tail = 0;
    }

    // ------------------------------
    // internal
    // ------------------------------

    private void ensureWritable(final int additionalBytes) {
        // 우선 앞쪽 공간(head) 회수로 해결 가능한지 시도
        compactIfNeeded();

        int required = tail + additionalBytes;
        if (required > buffer.length && head > 0) {
            compact();
            required = tail + additionalBytes;
        }
        if (required <= buffer.length) {
            return;
        }

        // 부족하면 확장(단, maxBytes를 넘지 않도록)
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

    private void compactIfNeeded() {
        if (head == 0) return;

        final int currentSize = readableBytes();
        final boolean shouldCompact = head > (buffer.length / 2) || tail == buffer.length;
        if (!shouldCompact) return;

        if (currentSize == 0) {
            head = 0;
            tail = 0;
            return;
        }
        compact();
    }

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
}
