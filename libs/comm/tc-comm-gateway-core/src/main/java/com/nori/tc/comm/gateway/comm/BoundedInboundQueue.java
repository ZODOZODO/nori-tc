package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.inbound.InboundChunk;
import com.nori.tc.comm.core.inbound.InboundQueue;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * eqp별 inbound 큐 구현체
 *
 * 핵심 요구사항
 * - 반드시 bounded 큐를 사용해야 합니다.
 * - 포화 시에는 false 반환 → 상위에서 DLQ/Quarantine 정책 수행
 */
public final class BoundedInboundQueue implements InboundQueue {

    private final ArrayBlockingQueue<InboundChunk> queue;

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param capacity 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public BoundedInboundQueue(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    
    /**
     * 게이트웨이 코어 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param chunk 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @return 처리 성공 여부
     */
    @Override
    public boolean offer(final InboundChunk chunk) {
        Objects.requireNonNull(chunk, "chunk is null");
        return queue.offer(chunk);
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    @Override
    public InboundChunk poll() {
        return queue.poll();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    @Override
    public int size() {
        return queue.size();
    }
}
