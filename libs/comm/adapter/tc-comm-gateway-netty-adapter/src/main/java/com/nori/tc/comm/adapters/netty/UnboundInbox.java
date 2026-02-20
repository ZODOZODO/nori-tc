package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * UNBOUND 상태 전용 바이트 임시 저장소입니다.
 *
 * <p>설계 의도:</p>
 * <p>1) Netty `channelRead`에서는 `byte[]` enqueue만 수행해 I/O 스레드 점유 시간을 최소화합니다.</p>
 * <p>2) 실제 버퍼 병합/파싱은 bind executor 스레드에서 수행합니다.</p>
 * <p>3) UNBOUND 단계 메모리 폭주를 막기 위해 총 누적 바이트를 제한합니다.</p>
 */
public final class UnboundInbox {

    private final int maxBytes;
    private final Deque<byte[]> chunks = new ArrayDeque<>();
    private final ReassemblyBuffer reassemblyBuffer;

    private int queuedBytes = 0;

    /**
     * UNBOUND inbox를 생성합니다.
     *
     * @param initialBytes 내부 재조립 버퍼 초기 크기
     * @param maxBytes 허용 가능한 최대 누적 바이트 수
     */
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
     * Netty I/O 스레드에서 raw 바이트를 큐에 적재합니다.
     *
     * <p>최대 허용 바이트를 초과하면 `false`를 반환해 상위에서 채널 종료 판단을 할 수 있게 합니다.</p>
     *
     * @param bytes 수신 raw 바이트
     * @return 적재 성공 여부
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
     * bind executor에서 큐 적재 데이터를 재조립 버퍼로 이동합니다.
     *
     * <p>이 단계에서 `ReassemblyBuffer` overflow가 발생하면 예외를 상위로 전달합니다.</p>
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

    /**
     * UNBOUND 재조립 버퍼를 반환합니다.
     *
     * @return 재조립 버퍼
     */
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
