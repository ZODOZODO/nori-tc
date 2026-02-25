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
     * UNBOUND 단계에 누적된 잔여 바이트를 모두 꺼내고 내부 상태를 초기화합니다.
     *
     * <p>사용 시점:</p>
     * <p>1) bind executor가 `INITIALIZE_REP`에서 eqpId 추출 성공</p>
     * <p>2) 채널이 BOUND 전환됨</p>
     * <p>3) UNBOUND 구간에 미리 도착한 일반 업무 메시지(예: TOOLEVENTS)를 유실 없이
     *    설비 inbox로 재주입(replay)해야 하는 시점</p>
     *
     * <p>중요:</p>
     * <p>- `chunks` 큐에 아직 남아 있는 raw chunk와 `reassemblyBuffer`에 이미 병합된 바이트를
     *   모두 합쳐서 반환합니다.</p>
     * <p>- 반환 이후에는 내부 상태를 완전히 비워, UNBOUND 단계 메모리가 남지 않도록 합니다.</p>
     *
     * @return UNBOUND 잔여 바이트 전체 스냅샷(없으면 길이 0 배열)
     */
    public synchronized byte[] drainAllBytesAndClear() {
        // bind executor가 마지막으로 남은 chunk까지 포함해 재조립 버퍼로 병합한 뒤 스냅샷을 생성합니다.
        drainToBuffer();

        final int readable = reassemblyBuffer.readableBytes();
        if (readable <= 0) {
            clear();
            return new byte[0];
        }

        final byte[] snapshot = reassemblyBuffer.copy(0, readable);
        clear();
        return snapshot;
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
