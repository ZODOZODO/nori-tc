package com.nori.tc.common.mailbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 공통 mailbox 스케줄링 알고리즘을 제공하는 도메인 중립 코디네이터입니다.
 *
 * <p>구현 의도는 gateway/business-core가 동일한 알고리즘을 재사용하되,
 * 실제 작업 실행 내용은 외부 handler로 위임하는 것입니다.</p>
 *
 * @param <T> 처리할 작업 타입
 */
public final class MailboxScheduler<T extends MailboxTask> {

    private static final Logger log = LoggerFactory.getLogger(MailboxScheduler.class);

    /**
     * 라우팅 키별 mailbox 저장소입니다.
     */
    private final Map<String, Mailbox<T>> mailboxMap = new ConcurrentHashMap<>();

    /**
     * 처리 준비된 라우팅 키를 전달하는 ReadyQueue입니다.
     */
    private final ReadyQueue readyQueue = new ReadyQueue();

    /**
     * 신규 mailbox 생성 시 사용할 기본 queue capacity입니다.
     */
    private final int mailboxCapacity;

    /**
     * 스케줄러를 생성합니다.
     *
     * @param mailboxCapacity mailbox 기본 capacity
     */
    public MailboxScheduler(final int mailboxCapacity) {
        if (mailboxCapacity <= 0) {
            throw new IllegalArgumentException("mailboxCapacity must be > 0");
        }
        this.mailboxCapacity = mailboxCapacity;
    }

    /**
     * 작업을 mailbox에 enqueue합니다.
     *
     * <p>enqueue 이후 inFlight가 비어있으면 ReadyQueue 등록을 시도합니다.</p>
     *
     * @param task enqueue 대상 작업
     * @param nowEpochMillis 현재 시각
     * @return enqueue 성공 여부
     */
    public boolean enqueue(final T task, final long nowEpochMillis) {
        Objects.requireNonNull(task, "task is null");

        final String routingKey = task.routingKey();
        final Mailbox<T> mailbox = mailboxMap.computeIfAbsent(routingKey, this::newMailbox);

        final boolean offered = mailbox.offer(task, nowEpochMillis);
        if (!offered) {
            if (log.isDebugEnabled()) {
                log.debug("메일박스 enqueue 거부(capacity 초과). routingKey={}, mailboxSize={}, mailboxCapacity={}",
                        routingKey,
                        mailbox.size(),
                        mailboxCapacity);
            }
            return false;
        }

        if (!mailbox.inFlightFlag().get() && mailbox.scheduledFlag().compareAndSet(false, true)) {
            readyQueue.offer(routingKey);
            if (log.isDebugEnabled()) {
                log.debug("라우팅 키를 ReadyQueue에 등록했습니다. routingKey={}, mailboxSize={}",
                        routingKey,
                        mailbox.size());
            }
        }
        return true;
    }

    /**
     * dispatcher/worker가 다음 라우팅 키를 가져오기 위해 호출합니다.
     *
     * @return 다음 라우팅 키
     * @throws InterruptedException 인터럽트 발생 시
     */
    public String takeReadyKey() throws InterruptedException {
        return readyQueue.take();
    }

    /**
     * 라우팅 키에 대한 실행 권한(in-flight)을 획득합니다.
     *
     * @param routingKey 라우팅 키
     * @return 실행 가능한 mailbox, 실패 시 null
     */
    public Mailbox<T> tryAcquire(final String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            return null;
        }

        final Mailbox<T> mailbox = mailboxMap.get(routingKey);
        if (mailbox == null) {
            if (log.isDebugEnabled()) {
                log.debug("실행 권한 획득 대상 메일박스가 없습니다. routingKey={}", routingKey);
            }
            return null;
        }

        mailbox.scheduledFlag().set(false);
        if (!mailbox.inFlightFlag().compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                log.debug("이미 실행 중인 메일박스입니다. routingKey={}", routingKey);
            }
            return null;
        }
        if (log.isDebugEnabled()) {
            log.debug("메일박스 실행 권한을 획득했습니다. routingKey={}, mailboxSize={}",
                    routingKey,
                    mailbox.size());
        }
        return mailbox;
    }

    /**
     * 작업 실행 종료 후 mailbox 상태를 해제하고, 잔여 작업이 있으면 재스케줄합니다.
     *
     * @param mailbox 해제 대상 mailbox
     */
    public void release(final Mailbox<T> mailbox) {
        if (mailbox == null) {
            return;
        }

        mailbox.inFlightFlag().set(false);

        if (!mailbox.isEmpty() && mailbox.scheduledFlag().compareAndSet(false, true)) {
            readyQueue.offer(mailbox.routingKey());
            if (log.isDebugEnabled()) {
                log.debug("메일박스 후속 작업을 ReadyQueue에 재등록했습니다. routingKey={}, remainingSize={}",
                        mailbox.routingKey(),
                        mailbox.size());
            }
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("메일박스 실행 권한을 해제했습니다. routingKey={}, remainingSize={}",
                    mailbox.routingKey(),
                    mailbox.size());
        }
    }

    /**
     * 라우팅 키에 연결된 mailbox를 조회합니다.
     *
     * @param routingKey 라우팅 키
     * @return mailbox, 없으면 null
     */
    public Mailbox<T> getMailbox(final String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            return null;
        }
        return mailboxMap.get(routingKey);
    }

    /**
     * 전체 mailbox 수를 반환합니다.
     *
     * @return mailbox 개수
     */
    public int mailboxCount() {
        return mailboxMap.size();
    }

    /**
     * ReadyQueue 길이를 반환합니다.
     *
     * @return ready queue size
     */
    public int readyQueueSize() {
        return readyQueue.size();
    }

    /**
     * 특정 라우팅 키의 스케줄러 mailbox 상태를 제거합니다.
     *
     * <p>설비 삭제/언바인드처럼 더 이상 해당 키를 사용하지 않을 때 호출합니다.
     * ReadyQueue에 이미 들어간 토큰은 이후 {@link #tryAcquire(String)} 단계에서 자연스럽게 무시됩니다.</p>
     *
     * @param routingKey 제거할 라우팅 키
     */
    public void removeMailbox(final String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            return;
        }
        final Mailbox<T> removed = mailboxMap.remove(routingKey);
        if (removed != null && log.isDebugEnabled()) {
            log.debug("메일박스 스케줄러 상태를 제거했습니다. routingKey={}", routingKey);
        }
    }

    /**
     * 신규 라우팅 키에 대한 메일박스를 생성합니다.
     *
     * @param routingKey 라우팅 키
     * @return 생성된 메일박스
     */
    private Mailbox<T> newMailbox(final String routingKey) {
        if (log.isDebugEnabled()) {
            log.debug("신규 메일박스를 생성합니다. routingKey={}, mailboxCapacity={}", routingKey, mailboxCapacity);
        }
        return new Mailbox<>(routingKey, mailboxCapacity);
    }
}
