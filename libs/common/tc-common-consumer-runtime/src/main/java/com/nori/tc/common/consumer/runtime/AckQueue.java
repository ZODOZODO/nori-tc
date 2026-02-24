package com.nori.tc.common.consumer.runtime;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 작업 처리 스레드가 생성한 ACK 이벤트를 소비 루프가 배치로 수집하기 위한 스레드 안전 큐입니다.
 *
 * <p>핵심 목적은 처리(worker)와 커밋(consumer loop) 경계를 분리하고,
 * {@code offer / drainTo} 중심의 단순한 API로 경합을 최소화하는 것입니다.</p>
 */
public final class AckQueue {

    private final BlockingQueue<AckEvent> queue = new LinkedBlockingQueue<>();

    /**
     * ACK 이벤트를 큐에 적재합니다.
     *
     * @param event 적재할 ACK 이벤트
     * @return 적재 성공 여부
     */
    public boolean offer(final AckEvent event) {
        Objects.requireNonNull(event, "event is null");
        return queue.offer(event);
    }

    /**
     * 최대 {@code maxElements}개까지 ACK 이벤트를 대상 컬렉션으로 이동합니다.
     *
     * @param target      수집 대상 컬렉션
     * @param maxElements 최대 수집 개수(1 이상)
     * @return 실제 이동된 이벤트 개수
     */
    public int drainTo(final Collection<AckEvent> target, final int maxElements) {
        Objects.requireNonNull(target, "target is null");
        if (maxElements <= 0) {
            throw new IllegalArgumentException("maxElements must be > 0");
        }
        return queue.drainTo(target, maxElements);
    }

    /**
     * 현재 대기 중인 ACK 이벤트 개수를 반환합니다.
     *
     * @return 큐 크기
     */
    public int size() {
        return queue.size();
    }
}
