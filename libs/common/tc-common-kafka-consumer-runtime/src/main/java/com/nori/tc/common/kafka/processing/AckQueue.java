package com.nori.tc.common.kafka.processing;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * worker 스레드에서 consumer poll 루프로 ack 이벤트를 전달하는 스레드 안전 큐입니다.
 *
 * <p>설계 의도:</p>
 * <p>1) 처리(worker)와 커밋(consumer) 실행 경계를 분리합니다.</p>
 * <p>2) 큐 연산은 `offer/drainTo` 중심으로 수행해 락 경합을 최소화합니다.</p>
 * <p>3) 커밋 루프는 배치 drain으로 ack를 수집해 오버헤드를 줄입니다.</p>
 */
public final class AckQueue {

    private final BlockingQueue<AckEvent> queue = new LinkedBlockingQueue<>();

    /**
     * ack 이벤트를 큐에 적재합니다.
     *
     * @param event ack 이벤트
     * @return 적재 성공 여부
     */
    public boolean offer(final AckEvent event) {
        Objects.requireNonNull(event, "event is null");
        return queue.offer(event);
    }

    /**
     * 최대 {@code maxElements}개까지 ack 이벤트를 target 컬렉션으로 옮깁니다.
     *
     * @param target 적재 대상 컬렉션
     * @param maxElements 최대 drain 개수(1 이상)
     * @return 실제 drain된 개수
     */
    public int drainTo(final Collection<AckEvent> target, final int maxElements) {
        Objects.requireNonNull(target, "target is null");
        if (maxElements <= 0) {
            throw new IllegalArgumentException("maxElements must be > 0");
        }
        return queue.drainTo(target, maxElements);
    }

    /**
     * 현재 큐 길이를 반환합니다.
     *
     * @return 대기 중인 ack 이벤트 개수
     */
    public int size() {
        return queue.size();
    }
}
