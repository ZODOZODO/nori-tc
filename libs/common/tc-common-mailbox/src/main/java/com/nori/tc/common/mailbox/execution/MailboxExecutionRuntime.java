package com.nori.tc.common.mailbox.execution;

import com.nori.tc.common.mailbox.Mailbox;
import com.nori.tc.common.mailbox.MailboxScheduler;
import com.nori.tc.common.mailbox.MailboxTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Mailbox 실행 런타임입니다.
 *
 * <p>공통 책임은 다음과 같습니다.</p>
 * <p>1) dispatcher/worker 스레드 생성 및 종료</p>
 * <p>2) ReadyQueue 소비 -> Mailbox 획득 -> task 디스패치</p>
 * <p>3) task 처리 후 Mailbox release 보장</p>
 * <p>4) 거절/실패/루프 예외를 전용 핸들러로 위임</p>
 *
 * <p>또한 `routingKey` 기반 로그 컨텍스트를 선택적으로 적용해
 * 모듈별 MDC 전파 정책을 지원합니다.</p>
 *
 * @param <T> Mailbox task 타입
 */
public final class MailboxExecutionRuntime<T extends MailboxTask> {

    private static final Logger log = LoggerFactory.getLogger(MailboxExecutionRuntime.class);

    /**
     * no-op closeable입니다.
     */
    private static final AutoCloseable NO_OP_CLOSEABLE = () -> {
        // no-op
    };

    /**
     * 라우팅 키를 알 수 없을 때 로그 표시에 사용하는 기본 값입니다.
     */
    private static final String UNKNOWN_ROUTING_KEY = "N/A";

    /**
     * 런타임 이름(로그 식별용)입니다.
     */
    private final String runtimeName;

    /**
     * Mailbox 스케줄러입니다.
     */
    private final MailboxScheduler<T> mailboxScheduler;

    /**
     * 런타임 설정입니다.
     */
    private final Config config;

    /**
     * task 처리기입니다.
     */
    private final TaskProcessor<T> taskProcessor;

    /**
     * worker 큐 거절 핸들러입니다.
     */
    private final TaskRejectedHandler<T> taskRejectedHandler;

    /**
     * task 실패 핸들러입니다.
     */
    private final TaskFailureHandler<T> taskFailureHandler;

    /**
     * dispatcher 루프 실패 핸들러입니다.
     */
    private final LoopFailureHandler loopFailureHandler;

    /**
     * 라우팅 키 로그 컨텍스트 전략입니다.
     */
    private final MailboxScheduler.RoutingKeyLogContext routingKeyLogContext;

    private volatile boolean running = false;
    private ExecutorService dispatcherPool;
    private ExecutorService workerPool;

    /**
     * 기본 핸들러(로그 출력)로 런타임을 생성합니다.
     *
     * @param runtimeName 런타임 이름
     * @param mailboxScheduler Mailbox 스케줄러
     * @param config 런타임 설정
     * @param taskProcessor task 처리기
     */
    public MailboxExecutionRuntime(
            final String runtimeName,
            final MailboxScheduler<T> mailboxScheduler,
            final Config config,
            final TaskProcessor<T> taskProcessor
    ) {
        this(
                runtimeName,
                mailboxScheduler,
                config,
                taskProcessor,
                null,
                null,
                null
        );
    }

    /**
     * 커스텀 핸들러를 포함해 런타임을 생성합니다.
     *
     * @param runtimeName 런타임 이름
     * @param mailboxScheduler Mailbox 스케줄러
     * @param config 런타임 설정
     * @param taskProcessor task 처리기
     * @param taskRejectedHandler worker 큐 거절 핸들러
     * @param taskFailureHandler task 실패 핸들러
     * @param loopFailureHandler 루프 실패 핸들러
     */
    public MailboxExecutionRuntime(
            final String runtimeName,
            final MailboxScheduler<T> mailboxScheduler,
            final Config config,
            final TaskProcessor<T> taskProcessor,
            final TaskRejectedHandler<T> taskRejectedHandler,
            final TaskFailureHandler<T> taskFailureHandler,
            final LoopFailureHandler loopFailureHandler
    ) {
        this.runtimeName = normalizeRuntimeName(runtimeName);
        this.mailboxScheduler = Objects.requireNonNull(mailboxScheduler, "mailboxScheduler is null");
        this.config = Objects.requireNonNull(config, "config is null");
        this.taskProcessor = Objects.requireNonNull(taskProcessor, "taskProcessor is null");
        this.taskRejectedHandler = taskRejectedHandler == null ? this::defaultRejectedHandler : taskRejectedHandler;
        this.taskFailureHandler = taskFailureHandler == null ? this::defaultTaskFailureHandler : taskFailureHandler;
        this.loopFailureHandler = loopFailureHandler == null ? this::defaultLoopFailureHandler : loopFailureHandler;
        this.routingKeyLogContext = this.config.routingKeyLogContext();
    }

    /**
     * 런타임을 시작합니다.
     */
    public synchronized void start() {
        if (running) {
            if (log.isDebugEnabled()) {
                log.debug("Mailbox runtime already running. runtime={}", runtimeName);
            }
            return;
        }
        running = true;

        dispatcherPool = Executors.newFixedThreadPool(
                config.dispatcherThreads(),
                namedThreadFactory(config.dispatcherThreadPrefix())
        );

        if (config.useWorkerPool()) {
            workerPool = Executors.newFixedThreadPool(
                    config.workerThreads(),
                    namedThreadFactory(config.workerThreadPrefix())
            );
        } else {
            workerPool = null;
        }

        for (int index = 0; index < config.dispatcherThreads(); index++) {
            dispatcherPool.execute(config.dispatcherRunnableDecorator().decorate(this::runDispatcherLoop));
        }

        log.info("Mailbox runtime started. runtime={}, dispatcherThreads={}, workerThreads={}, mode={}",
                runtimeName,
                config.dispatcherThreads(),
                config.workerThreads(),
                config.useWorkerPool() ? "ASYNC_WORKER_POOL" : "DIRECT");
    }

    /**
     * 런타임을 종료합니다.
     */
    public synchronized void stop() {
        if (!running) {
            if (log.isDebugEnabled()) {
                log.debug("Mailbox runtime already stopped. runtime={}", runtimeName);
            }
            return;
        }
        running = false;

        shutdownExecutor(workerPool, runtimeName + "-worker");
        shutdownExecutor(dispatcherPool, runtimeName + "-dispatcher");
        workerPool = null;
        dispatcherPool = null;

        log.info("Mailbox runtime stopped. runtime={}", runtimeName);
    }

    /**
     * 실행 상태를 반환합니다.
     *
     * @return 실행 중이면 true
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * dispatcher 루프 본문입니다.
     */
    private void runDispatcherLoop() {
        while (running) {
            try {
                final String routingKey = mailboxScheduler.takeReadyKey();
                final Mailbox<T> mailbox = callWithRoutingKeyLogContext(
                        routingKey,
                        () -> mailboxScheduler.tryAcquire(routingKey)
                );
                if (mailbox == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Dispatcher skipped because mailbox acquire returned null. runtime={}, routingKey={}",
                                runtimeName,
                                routingKey);
                    }
                    continue;
                }

                final T task = mailbox.poll();
                if (task == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Dispatcher acquired mailbox without task. runtime={}, routingKey={}",
                                runtimeName,
                                mailbox.routingKey());
                    }
                    releaseMailbox(mailbox);
                    continue;
                }

                dispatchTask(mailbox, task);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                loopFailureHandler.onLoopFailure(ex);
            }
        }
    }

    /**
     * task를 direct 또는 worker pool로 전달합니다.
     *
     * @param mailbox 획득된 Mailbox
     * @param task 처리 대상 task
     */
    private void dispatchTask(final Mailbox<T> mailbox, final T task) {
        if (workerPool == null) {
            processTask(mailbox, task);
            return;
        }

        try {
            final Runnable processRunnable = config.workerRunnableDecorator()
                    .decorate(() -> processTask(mailbox, task));
            workerPool.execute(processRunnable);
        } catch (RejectedExecutionException rejected) {
            final String routingKey = resolveRoutingKey(mailbox, task);
            withRoutingKeyLogContext(routingKey, () -> taskRejectedHandler.onRejected(task, rejected));
            releaseMailbox(mailbox);
        }
    }

    /**
     * 단일 task를 처리하고 Mailbox release를 보장합니다.
     *
     * @param mailbox 처리 대상 Mailbox
     * @param task 처리 대상 task
     */
    private void processTask(final Mailbox<T> mailbox, final T task) {
        final String routingKey = resolveRoutingKey(mailbox, task);
        try {
            taskProcessor.process(task);
        } catch (Exception ex) {
            withRoutingKeyLogContext(routingKey, () -> taskFailureHandler.onTaskFailure(task, ex));
        } finally {
            releaseMailbox(mailbox);
        }
    }

    /**
     * Mailbox release를 라우팅 키 컨텍스트와 함께 수행합니다.
     *
     * @param mailbox release 대상 Mailbox
     */
    private void releaseMailbox(final Mailbox<T> mailbox) {
        if (mailbox == null) {
            return;
        }
        withRoutingKeyLogContext(mailbox.routingKey(), () -> mailboxScheduler.release(mailbox));
    }

    /**
     * executor를 종료합니다.
     *
     * @param executor 종료 대상 executor
     * @param executorName 로그 출력용 executor 이름
     */
    private void shutdownExecutor(final ExecutorService executor, final String executorName) {
        if (executor == null) {
            return;
        }

        executor.shutdownNow();
        try {
            final boolean terminated = executor.awaitTermination(config.shutdownWaitMs(), TimeUnit.MILLISECONDS);
            if (!terminated && log.isDebugEnabled()) {
                log.debug("Executor did not terminate in time. runtime={}, executor={}, waitMs={}",
                        runtimeName,
                        executorName,
                        config.shutdownWaitMs());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 이름 prefix 기반 데몬 스레드 팩토리를 생성합니다.
     *
     * @param threadPrefix 스레드 이름 prefix
     * @return thread factory
     */
    private ThreadFactory namedThreadFactory(final String threadPrefix) {
        final AtomicInteger sequence = new AtomicInteger(0);
        return runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(threadPrefix + sequence.incrementAndGet());
            return thread;
        };
    }

    /**
     * 기본 worker 거절 핸들러입니다.
     *
     * @param task 거절된 task
     * @param rejected 거절 예외
     */
    private void defaultRejectedHandler(final T task, final RejectedExecutionException rejected) {
        final String routingKey = taskRoutingKey(task);
        withRoutingKeyLogContext(routingKey, () -> log.error(
                "Mailbox task rejected by worker pool. runtime={}, routingKey={}",
                runtimeName,
                routingKey == null ? UNKNOWN_ROUTING_KEY : routingKey,
                rejected
        ));
    }

    /**
     * 기본 task 실패 핸들러입니다.
     *
     * @param task 실패 task
     * @param ex 처리 예외
     */
    private void defaultTaskFailureHandler(final T task, final Exception ex) {
        final String routingKey = taskRoutingKey(task);
        withRoutingKeyLogContext(routingKey, () -> log.error(
                "Mailbox task processing failed. runtime={}, routingKey={}",
                runtimeName,
                routingKey == null ? UNKNOWN_ROUTING_KEY : routingKey,
                ex
        ));
    }

    /**
     * 기본 루프 실패 핸들러입니다.
     *
     * @param ex 루프 예외
     */
    private void defaultLoopFailureHandler(final Exception ex) {
        log.error("Mailbox dispatcher loop failed. runtime={}", runtimeName, ex);
    }

    /**
     * task/ mailbox 정보에서 routingKey를 해석합니다.
     *
     * @param mailbox 처리 mailbox
     * @param task 처리 task
     * @return 해석된 routingKey
     */
    private String resolveRoutingKey(final Mailbox<T> mailbox, final T task) {
        final String taskRoutingKey = taskRoutingKey(task);
        if (taskRoutingKey != null && !taskRoutingKey.isBlank()) {
            return taskRoutingKey;
        }
        if (mailbox == null) {
            return null;
        }
        return mailbox.routingKey();
    }

    /**
     * task에서 routingKey를 안전하게 추출합니다.
     *
     * @param task 대상 task
     * @return routingKey, 없으면 null
     */
    private String taskRoutingKey(final T task) {
        if (task == null) {
            return null;
        }
        return task.routingKey();
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
        } catch (Exception openException) {
            if (log.isDebugEnabled()) {
                log.debug("RoutingKey log context open failed. runtime={}, routingKey={}",
                        runtimeName,
                        routingKey,
                        openException);
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
                log.debug("RoutingKey log context close failed. runtime={}, routingKey={}",
                        runtimeName,
                        routingKey,
                        closeException);
            }
        }
    }

    /**
     * 라우팅 키 로그 컨텍스트를 적용한 상태로 값을 반환합니다.
     *
     * <p>supplier는 최대 한 번만 실행되도록 보장합니다.
     * 컨텍스트 close 예외가 발생하더라도 supplier를 재실행하지 않습니다.</p>
     *
     * @param routingKey 라우팅 키
     * @param supplier 실행 함수
     * @return 반환값
     * @param <R> 반환 타입
     */
    private <R> R callWithRoutingKeyLogContext(final String routingKey, final Supplier<R> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");

        final AutoCloseable context;
        try {
            context = safeContext(routingKeyLogContext.open(routingKey));
        } catch (Exception openException) {
            if (log.isDebugEnabled()) {
                log.debug("RoutingKey log context open failed. runtime={}, routingKey={}",
                        runtimeName,
                        routingKey,
                        openException);
            }
            return supplier.get();
        }

        try {
            return supplier.get();
        } finally {
            try {
                context.close();
            } catch (Exception closeException) {
                if (log.isDebugEnabled()) {
                    log.debug("RoutingKey log context close failed. runtime={}, routingKey={}",
                            runtimeName,
                            routingKey,
                            closeException);
                }
            }
        }
    }

    /**
     * null 컨텍스트를 no-op 컨텍스트로 치환합니다.
     *
     * @param context 원본 컨텍스트
     * @return null-safe 컨텍스트
     */
    private static AutoCloseable safeContext(final AutoCloseable context) {
        return context == null ? NO_OP_CLOSEABLE : context;
    }

    /**
     * 런타임 이름을 정규화합니다.
     *
     * @param runtimeName 입력 런타임 이름
     * @return 정규화된 런타임 이름
     */
    private static String normalizeRuntimeName(final String runtimeName) {
        if (runtimeName == null || runtimeName.isBlank()) {
            return "mailbox-runtime";
        }
        return runtimeName.trim();
    }

    /**
     * Mailbox 실행 설정입니다.
     *
     * @param dispatcherThreads dispatcher 스레드 수
     * @param workerThreads worker 스레드 수(0이면 direct 모드)
     * @param shutdownWaitMs 종료 대기 시간(ms)
     * @param dispatcherThreadPrefix dispatcher 스레드 prefix
     * @param workerThreadPrefix worker 스레드 prefix
     * @param dispatcherRunnableDecorator dispatcher runnable 데코레이터
     * @param workerRunnableDecorator worker runnable 데코레이터
     * @param routingKeyLogContext 라우팅 키 로그 컨텍스트 전략
     */
    public record Config(
            int dispatcherThreads,
            int workerThreads,
            long shutdownWaitMs,
            String dispatcherThreadPrefix,
            String workerThreadPrefix,
            RunnableDecorator dispatcherRunnableDecorator,
            RunnableDecorator workerRunnableDecorator,
            MailboxScheduler.RoutingKeyLogContext routingKeyLogContext
    ) {

        /**
         * 설정값을 검증하고 기본값을 보정합니다.
         */
        public Config {
            if (dispatcherThreads <= 0) {
                throw new IllegalArgumentException("dispatcherThreads must be > 0");
            }
            if (workerThreads < 0) {
                throw new IllegalArgumentException("workerThreads must be >= 0");
            }
            if (shutdownWaitMs <= 0L) {
                throw new IllegalArgumentException("shutdownWaitMs must be > 0");
            }

            if (dispatcherThreadPrefix == null || dispatcherThreadPrefix.isBlank()) {
                dispatcherThreadPrefix = "mailbox-dispatcher-";
            }
            if (workerThreadPrefix == null || workerThreadPrefix.isBlank()) {
                workerThreadPrefix = "mailbox-worker-";
            }
            if (dispatcherRunnableDecorator == null) {
                dispatcherRunnableDecorator = RunnableDecorator.identity();
            }
            if (workerRunnableDecorator == null) {
                workerRunnableDecorator = RunnableDecorator.identity();
            }
            if (routingKeyLogContext == null) {
                routingKeyLogContext = MailboxScheduler.RoutingKeyLogContext.noOp();
            }
        }

        /**
         * direct 모드 설정을 생성합니다.
         *
         * @param dispatcherThreads dispatcher 스레드 수
         * @param shutdownWaitMs 종료 대기(ms)
         * @param dispatcherThreadPrefix dispatcher prefix
         * @return direct 설정
         */
        public static Config direct(
                final int dispatcherThreads,
                final long shutdownWaitMs,
                final String dispatcherThreadPrefix
        ) {
            return new Config(
                    dispatcherThreads,
                    0,
                    shutdownWaitMs,
                    dispatcherThreadPrefix,
                    "mailbox-worker-",
                    RunnableDecorator.identity(),
                    RunnableDecorator.identity(),
                    MailboxScheduler.RoutingKeyLogContext.noOp()
            );
        }

        /**
         * async 모드 설정을 생성합니다.
         *
         * @param dispatcherThreads dispatcher 스레드 수
         * @param workerThreads worker 스레드 수
         * @param shutdownWaitMs 종료 대기(ms)
         * @param dispatcherThreadPrefix dispatcher prefix
         * @param workerThreadPrefix worker prefix
         * @return async 설정
         */
        public static Config async(
                final int dispatcherThreads,
                final int workerThreads,
                final long shutdownWaitMs,
                final String dispatcherThreadPrefix,
                final String workerThreadPrefix
        ) {
            return new Config(
                    dispatcherThreads,
                    workerThreads,
                    shutdownWaitMs,
                    dispatcherThreadPrefix,
                    workerThreadPrefix,
                    RunnableDecorator.identity(),
                    RunnableDecorator.identity(),
                    MailboxScheduler.RoutingKeyLogContext.noOp()
            );
        }

        /**
         * worker pool 사용 여부를 반환합니다.
         *
         * @return workerThreads > 0 이면 true
         */
        public boolean useWorkerPool() {
            return workerThreads > 0;
        }

        /**
         * dispatcher 데코레이터를 교체한 설정을 반환합니다.
         *
         * @param decorator 적용할 데코레이터
         * @return 새 설정
         */
        public Config withDispatcherDecorator(final RunnableDecorator decorator) {
            return new Config(
                    dispatcherThreads,
                    workerThreads,
                    shutdownWaitMs,
                    dispatcherThreadPrefix,
                    workerThreadPrefix,
                    decorator,
                    workerRunnableDecorator,
                    routingKeyLogContext
            );
        }

        /**
         * worker 데코레이터를 교체한 설정을 반환합니다.
         *
         * @param decorator 적용할 데코레이터
         * @return 새 설정
         */
        public Config withWorkerDecorator(final RunnableDecorator decorator) {
            return new Config(
                    dispatcherThreads,
                    workerThreads,
                    shutdownWaitMs,
                    dispatcherThreadPrefix,
                    workerThreadPrefix,
                    dispatcherRunnableDecorator,
                    decorator,
                    routingKeyLogContext
            );
        }

        /**
         * 라우팅 키 로그 컨텍스트 전략을 교체한 설정을 반환합니다.
         *
         * @param logContext 로그 컨텍스트 전략
         * @return 새 설정
         */
        public Config withRoutingKeyLogContext(final MailboxScheduler.RoutingKeyLogContext logContext) {
            return new Config(
                    dispatcherThreads,
                    workerThreads,
                    shutdownWaitMs,
                    dispatcherThreadPrefix,
                    workerThreadPrefix,
                    dispatcherRunnableDecorator,
                    workerRunnableDecorator,
                    logContext
            );
        }
    }

    /**
     * task 처리 함수형 인터페이스입니다.
     *
     * @param <T> task 타입
     */
    @FunctionalInterface
    public interface TaskProcessor<T extends MailboxTask> {

        /**
         * 단일 task를 처리합니다.
         *
         * @param task 처리 대상
         * @throws Exception 처리 실패 예외
         */
        void process(T task) throws Exception;
    }

    /**
     * worker 큐 거절 핸들러 인터페이스입니다.
     *
     * @param <T> task 타입
     */
    @FunctionalInterface
    public interface TaskRejectedHandler<T extends MailboxTask> {

        /**
         * task 거절 시 호출됩니다.
         *
         * @param task 거절된 task
         * @param rejected 거절 예외
         */
        void onRejected(T task, RejectedExecutionException rejected);
    }

    /**
     * task 실패 핸들러 인터페이스입니다.
     *
     * @param <T> task 타입
     */
    @FunctionalInterface
    public interface TaskFailureHandler<T extends MailboxTask> {

        /**
         * task 처리 실패 시 호출됩니다.
         *
         * @param task 실패 task
         * @param ex 실패 예외
         */
        void onTaskFailure(T task, Exception ex);
    }

    /**
     * 루프 실패 핸들러 인터페이스입니다.
     */
    @FunctionalInterface
    public interface LoopFailureHandler {

        /**
         * dispatcher 루프 예외 발생 시 호출됩니다.
         *
         * @param ex 루프 예외
         */
        void onLoopFailure(Exception ex);
    }

    /**
     * Runnable 데코레이터 인터페이스입니다.
     */
    @FunctionalInterface
    public interface RunnableDecorator {

        /**
         * runnable을 데코레이션합니다.
         *
         * @param runnable 원본 runnable
         * @return 데코레이션된 runnable
         */
        Runnable decorate(Runnable runnable);

        /**
         * 아무 동작도 하지 않는 기본 데코레이터를 반환합니다.
         *
         * @return identity 데코레이터
         */
        static RunnableDecorator identity() {
            return runnable -> runnable;
        }
    }
}
