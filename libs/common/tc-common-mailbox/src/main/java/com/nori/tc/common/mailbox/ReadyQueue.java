package com.nori.tc.common.mailbox;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 라우팅 키 토큰을 운반하는 전역 ReadyQueue입니다.
 *
 * <p>ReadyQueue는 키 문자열만 보관하며, 실제 mailbox 상태 판단은
 * 스케줄러 계층에서 수행합니다.</p>
 */
public final class ReadyQueue {

    /**
     * 라우팅 키를 저장하는 내부 blocking queue입니다.
     */
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    /**
     * 라우팅 키를 queue에 등록합니다.
     *
     * @param routingKey 라우팅 키
     */
    public void offer(final String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            return;
        }
        queue.offer(routingKey);
    }

    /**
     * 다음 라우팅 키를 blocking 방식으로 가져옵니다.
     *
     * @return 라우팅 키
     * @throws InterruptedException 인터럽트 발생 시
     */
    public String take() throws InterruptedException {
        return queue.take();
    }

    /**
     * 큐 크기를 반환합니다.
     *
     * @return queue size
     */
    public int size() {
        return queue.size();
    }
}
