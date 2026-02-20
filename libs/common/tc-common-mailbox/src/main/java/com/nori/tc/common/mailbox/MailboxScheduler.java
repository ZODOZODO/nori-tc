package com.nori.tc.common.mailbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 라우팅 키 단위로 Mailbox를 스케줄링하는 공통 컴포넌트입니다.
 *
 * <p>핵심 역할은 다음과 같습니다.</p>
 * <p>1) `routingKey` 별 Mailbox를 생성/관리합니다.</p>
 * <p>2) enqueue 시 실행 가능한 라우팅 키를 ReadyQueue에 등록합니다.</p>
 * <p>3) `tryAcquire`로 in-flight 권한을 단일하게 부여합니다.</p>
 * <p>4) `release`에서 잔여 작업을 확인해 재스케줄 여부를 결정합니다.</p>
 *
 * <p>필요 시 `routingKey`를 로그 컨텍스트(MDC)로 승격할 수 있도록
 * `RoutingKeyLogContext` 확장 포인트를 제공합니다.</p>
 *
 * @param <T> Mailbox에 적재되는 작업 타입
 */
public final class MailboxScheduler<T extends MailboxTask> {

    private static final Logger log = LoggerFactory.getLogger(MailboxScheduler.class);

    /**
     * 라우팅 키별 Mailbox 저장소입니다.
     */
    private final Map<String, Mailbox<T>> mailboxMap = new ConcurrentHashMap<>();

    /**
     * 실행 준비된 라우팅 키를 담는 ReadyQueue입니다.
     */
    private final ReadyQueue readyQueue = new ReadyQueue();

    /**
     * 신규 Mailbox 생성 시 사용할 기본 큐 용량입니다.
     */
    private final int mailboxCapacity;

    /**
     * 라우팅 키 기반 로그 컨텍스트 적용 전략입니다.
     */
    private final RoutingKeyLogContext routingKeyLogContext;

    /**
     * no-op closeable 싱글턴입니다.
     */
    private static final AutoCloseable NO_OP_CLOSEABLE = () -> {
        // no-op
    };

    /**
     * 기본 생성자입니다.
     *
     * <p>로그 컨텍스트 전략은 no-op으로 동작합니다.</p>
     *
     * @param mailboxCapacity Mailbox 기본 용량
     */
    public MailboxScheduler(final int mailboxCapacity) {
        this(mailboxCapacity, RoutingKeyLogContext.noOp());
    }

    /**
     * 로그 컨텍스트 전략을 포함해 스케줄러를 생성합니다.
     *
     * @param mailboxCapacity Mailbox 기본 용량
     * @param routingKeyLogContext 라우팅 키 로그 컨텍스트 전략
     */
    public MailboxScheduler(
            final int mailboxCapacity,
            final RoutingKeyLogContext routingKeyLogContext
    ) {
        if (mailboxCapacity <= 0) {
            throw new IllegalArgumentException("mailboxCapacity must be > 0");
        }
        this.mailboxCapacity = mailboxCapacity;
        this.routingKeyLogContext = routingKeyLogContext == null
                ? RoutingKeyLogContext.noOp()
                : routingKeyLogContext;
    }

    /**
     * 작업을 해당 라우팅 키 Mailbox에 enqueue 합니다.
     *
     * <p>enqueue 성공 후 in-flight=false, scheduled=false 조건이면
     * ReadyQueue에 라우팅 키를 등록해 dispatcher가 처리할 수 있게 합니다.</p>
     * <p>task.routingKey는 필수 값이며, null/blank이면 예외를 발생시켜 잘못된 사용을 즉시 차단합니다.</p>
     *
     * @param task enqueue 대상 작업
     * @param nowEpochMillis 현재 시각(epoch millis)
     * @return enqueue 성공 여부
     */
    public boolean enqueue(final T task, final long nowEpochMillis) {
        Objects.requireNonNull(task, "task is null");

        final String routingKey = task.routingKey();
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("task.routingKey() is required");
        }
        final Mailbox<T> mailbox = mailboxMap.computeIfAbsent(routingKey, this::newMailbox);

        final boolean offered = mailbox.offer(task, nowEpochMillis);
        if (!offered) {
            if (log.isDebugEnabled()) {
                withRoutingKeyLogContext(routingKey, () -> log.debug(
                        "Mailbox enqueue rejected by capacity. routingKey={}, mailboxSize={}, mailboxCapacity={}",
                        routingKey,
                        mailbox.size(),
                        mailboxCapacity
                ));
            }
            return false;
        }

        if (!mailbox.inFlightFlag().get() && mailbox.scheduledFlag().compareAndSet(false, true)) {
            readyQueue.offer(routingKey);
            if (log.isDebugEnabled()) {
                withRoutingKeyLogContext(routingKey, () -> log.debug(
                        "Routing key registered to ReadyQueue. routingKey={}, mailboxSize={}",
                        routingKey,
                        mailbox.size()
                ));
            }
        }
        return true;
    }

    /**
     * 다음 실행 라우팅 키를 블로킹 대기 후 반환합니다.
     *
     * @return 다음 라우팅 키
     * @throws InterruptedException 인터럽트 발생 시
     */
    public String takeReadyKey() throws InterruptedException {
        return readyQueue.take();
    }

    /**
     * 라우팅 키 Mailbox의 in-flight 실행 권한을 획득합니다.
     *
     * @param routingKey 대상 라우팅 키
     * @return 획득된 Mailbox, 실패 시 null
     */
    public Mailbox<T> tryAcquire(final String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            return null;
        }

        final Mailbox<T> mailbox = mailboxMap.get(routingKey);
        if (mailbox == null) {
            if (log.isDebugEnabled()) {
                withRoutingKeyLogContext(routingKey, () -> log.debug(
                        "Mailbox acquire skipped because mailbox does not exist. routingKey={}",
                        routingKey
                ));
            }
            return null;
        }

        mailbox.scheduledFlag().set(false);
        if (!mailbox.inFlightFlag().compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                withRoutingKeyLogContext(routingKey, () -> log.debug(
                        "Mailbox acquire skipped because mailbox is already in-flight. routingKey={}",
                        routingKey
                ));
            }
            return null;
        }

        if (log.isDebugEnabled()) {
            withRoutingKeyLogContext(routingKey, () -> log.debug(
                    "Mailbox acquire granted. routingKey={}, mailboxSize={}",
                    routingKey,
                    mailbox.size()
            ));
        }
        return mailbox;
    }

    /**
     * 작업 처리 후 in-flight를 해제하고 필요 시 재스케줄합니다.
     *
     * @param mailbox 해제 대상 Mailbox
     */
    public void release(final Mailbox<T> mailbox) {
        if (mailbox == null) {
            return;
        }

        final String routingKey = mailbox.routingKey();
        mailbox.inFlightFlag().set(false);

        if (!mailbox.isEmpty() && mailbox.scheduledFlag().compareAndSet(false, true)) {
            readyQueue.offer(routingKey);
            if (log.isDebugEnabled()) {
                withRoutingKeyLogContext(routingKey, () -> log.debug(
                        "Mailbox re-scheduled to ReadyQueue after release. routingKey={}, remainingSize={}",
                        routingKey,
                        mailbox.size()
                ));
            }
            return;
        }

        if (log.isDebugEnabled()) {
            withRoutingKeyLogContext(routingKey, () -> log.debug(
                    "Mailbox release completed. routingKey={}, remainingSize={}",
                    routingKey,
                    mailbox.size()
            ));
        }
    }

    /**
     * 라우팅 키로 Mailbox를 조회합니다.
     *
     * @param routingKey 조회 라우팅 키
     * @return Mailbox, 없으면 null
     */
    public Mailbox<T> getMailbox(final String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            return null;
        }
        return mailboxMap.get(routingKey);
    }

    /**
     * 현재 등록된 Mailbox 수를 반환합니다.
     *
     * @return Mailbox 개수
     */
    public int mailboxCount() {
        return mailboxMap.size();
    }

    /**
     * ReadyQueue 크기를 반환합니다.
     *
     * @return ReadyQueue size
     */
    public int readyQueueSize() {
        return readyQueue.size();
    }

    /**
     * 특정 라우팅 키의 스케줄러 상태를 제거합니다.
     *
     * <p>이미 ReadyQueue에 들어간 토큰은 이후 `tryAcquire` 단계에서 자연스럽게 무시됩니다.</p>
     *
     * @param routingKey 제거 대상 라우팅 키
     */
    public void removeMailbox(final String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            return;
        }
        final Mailbox<T> removed = mailboxMap.remove(routingKey);
        if (removed != null && log.isDebugEnabled()) {
            withRoutingKeyLogContext(routingKey, () -> log.debug(
                    "Mailbox scheduler state removed. routingKey={}",
                    routingKey
            ));
        }
    }

    /**
     * 신규 라우팅 키용 Mailbox를 생성합니다.
     *
     * @param routingKey 라우팅 키
     * @return 생성된 Mailbox
     */
    private Mailbox<T> newMailbox(final String routingKey) {
        if (log.isDebugEnabled()) {
            withRoutingKeyLogContext(routingKey, () -> log.debug(
                    "Creating new mailbox. routingKey={}, mailboxCapacity={}",
                    routingKey,
                    mailboxCapacity
            ));
        }
        return new Mailbox<>(routingKey, mailboxCapacity);
    }

    /**
     * 라우팅 키 로그 컨텍스트를 적용한 상태로 작업을 실행합니다.
     *
     * @param routingKey 라우팅 키
     * @param task 실행 작업
     */
    private void withRoutingKeyLogContext(final String routingKey, final Runnable task) {
        if (task == null) {
            return;
        }

        final AutoCloseable context;
        try {
            context = safeContext(routingKeyLogContext.open(routingKey));
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("RoutingKey log context open failed. routingKey={}", routingKey, ex);
            }
            task.run();
            return;
        }

        try (AutoCloseable ignored = context) {
            task.run();
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception closeException) {
            if (log.isDebugEnabled()) {
                log.debug("RoutingKey log context close failed. routingKey={}", routingKey, closeException);
            }
        }
    }

    /**
     * null 컨텍스트를 no-op 컨텍스트로 치환합니다.
     *
     * @param context 반환된 컨텍스트
     * @return null-safe 컨텍스트
     */
    private static AutoCloseable safeContext(final AutoCloseable context) {
        return context == null ? NO_OP_CLOSEABLE : context;
    }

    /**
     * 라우팅 키 기반 로그 컨텍스트 확장 포인트입니다.
     */
    @FunctionalInterface
    public interface RoutingKeyLogContext {

        /**
         * 라우팅 키 컨텍스트를 오픈합니다.
         *
         * @param routingKey 라우팅 키
         * @return 해제 가능한 컨텍스트
         */
        AutoCloseable open(String routingKey);

        /**
         * 컨텍스트를 적용하지 않는 no-op 전략을 반환합니다.
         *
         * @return no-op 라우팅 키 컨텍스트
         */
        static RoutingKeyLogContext noOp() {
            return routingKey -> NO_OP_CLOSEABLE;
        }
    }
}
