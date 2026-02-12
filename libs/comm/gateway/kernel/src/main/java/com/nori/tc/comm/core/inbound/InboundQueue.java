package com.nori.tc.comm.core.inbound;

/**
 * eqp별 inbound 큐(코어 관점)
 *
 * 요구사항(통합 tc-comm-gateway 안정성 핵심)
 * - 반드시 bounded 큐여야 합니다(무한 큐 금지)
 * - offer 실패(포화) 시, "유실" 대신 DLQ/Quarantine 같은 운영 정책으로 처리해야 합니다.
 *
 * 구현체 예)
 * - ArrayBlockingQueue 기반 어댑터(앱 레이어)
 * - 고성능 MPSC 큐(추후)
 */
public interface InboundQueue {

    /**
     * 큐에 chunk 적재
     *
     * @return 적재 성공 true, 큐 포화 등으로 실패하면 false
     */
    boolean offer(InboundChunk chunk);

    /**
     * 큐에서 1건 꺼내기(없으면 null)
     */
    InboundChunk poll();

    /**
     * 큐 크기(관측용)
     */
    int size();
}
