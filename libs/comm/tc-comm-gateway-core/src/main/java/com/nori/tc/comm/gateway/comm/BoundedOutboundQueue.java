package com.nori.tc.comm.gateway.comm;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * eqp별 outbound 큐 구현체
 *
 * - bounded 필수
 * - offer 실패 시 상위 정책(재시도/close/metric)으로 처리
 */
public final class BoundedOutboundQueue {

    private final ArrayBlockingQueue<OutboundCommand> queue;

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param capacity 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public BoundedOutboundQueue(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    
    /**
     * 게이트웨이 코어 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return 처리 성공 여부
     */
    public boolean offer(final OutboundCommand command) {
        Objects.requireNonNull(command, "command is null");
        return queue.offer(command);
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public OutboundCommand poll() {
        return queue.poll();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int size() {
        return queue.size();
    }
}
