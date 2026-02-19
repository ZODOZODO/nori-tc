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

/**
 * Mailbox 기반 실행 루프를 공통화한 런타임입니다.
 *
 * <p>이 클래스는 다음 공통 책임을 담당합니다.</p>
 * <p>1) dispatcher/worker 스레드풀 생성 및 종료</p>
 * <p>2) ReadyQueue 토큰 소비 -> mailbox 획득 -> task 전달 루프</p>
 * <p>3) task 처리 후 mailbox release 보장</p>
 * <p>4) task 거부/실패/루프 예외를 훅으로 앱 계층에 전달</p>
 *
 * <p>앱 계층(gateway/business)은 도메인 처리 로직만 {@link TaskProcessor}로 주입하면 됩니다.</p>
 *
 * @param <T> mailbox task 타입
 */
public final class MailboxExecutionRuntime<T extends MailboxTask> {

    private static final Logger log = LoggerFactory.getLogger(MailboxExecutionRuntime.class);

    /**
     * 런타임 식별자입니다.
     *
     * <p>로그 라벨 용도로 사용합니다.</p>
     */
    private final String runtimeName;

    /**
     * 공통 mailbox 스케줄러입니다.
     */
    private final MailboxScheduler<T> mailboxScheduler;

    /**
     * 실행 옵션입니다.
     */
    private final Config config;

    /**
     * 앱 계층의 실제 task 처리기입니다.
     */
    private final TaskProcessor<T> taskProcessor;

    /**
     * worker 큐 거부 시 호출되는 훅입니다.
     */
    private final TaskRejectedHandler<T> taskRejectedHandler;

    /**
     * task 처리 실패 시 호출되는 훅입니다.
     */
    private final TaskFailureHandler<T> taskFailureHandler;

    /**
     * dispatcher 루프 레벨 예외 훅입니다.
     */
    private final LoopFailureHandler loopFailureHandler;

    private volatile boolean running = false;
    private ExecutorService dispatcherPool;
    private ExecutorService workerPool;

    /**
     * 기본 훅(로그 출력)으로 런타임을 생성합니다.
     *
     * @param runtimeName 런타임 이름
     * @param mailboxScheduler mailbox 스케줄러
     * @param config 실행 옵션
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
     * 모든 훅을 주입받아 런타임을 생성합니다.
     *
     * @param runtimeName 런타임 이름
     * @param mailboxScheduler mailbox 스케줄러
     * @param config 실행 옵션
     * @param taskProcessor task 처리기
     * @param taskRejectedHandler worker 큐 거부 훅
     * @param taskFailureHandler task 처리 실패 훅
     * @param loopFailureHandler 루프 실패 훅
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
    }

    /**
     * dispatcher/worker 루프를 시작합니다.
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
     *
     * <p>ReadyQueue 토큰을 소비하고 mailbox 단위로 task를 꺼내 처리기로 전달합니다.</p>
     */
    private void runDispatcherLoop() {
        while (running) {
            try {
                final String routingKey = mailboxScheduler.takeReadyKey();
                final Mailbox<T> mailbox = mailboxScheduler.tryAcquire(routingKey);
                if (mailbox == null) {
                    continue;
                }

                final T task = mailbox.poll();
                if (task == null) {
                    mailboxScheduler.release(mailbox);
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
     * @param mailbox 실행 권한을 획득한 mailbox
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
            taskRejectedHandler.onRejected(task, rejected);
            mailboxScheduler.release(mailbox);
        }
    }

    /**
     * 단일 task를 처리하고 mailbox release를 보장합니다.
     *
     * @param mailbox 처리 대상 mailbox
     * @param task 처리 대상 task
     */
    private void processTask(final Mailbox<T> mailbox, final T task) {
        try {
            taskProcessor.process(task);
        } catch (Exception ex) {
            taskFailureHandler.onTaskFailure(task, ex);
        } finally {
            mailboxScheduler.release(mailbox);
        }
    }

    /**
     * 스레드풀을 종료합니다.
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
     * thread name prefix 기반 데몬 스레드 팩토리를 생성합니다.
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
     * 기본 worker 거부 핸들러입니다.
     *
     * @param task 거부된 task
     * @param rejected 거부 예외
     */
    private void defaultRejectedHandler(final T task, final RejectedExecutionException rejected) {
        log.error("Mailbox task rejected by worker pool. runtime={}, routingKey={}",
                runtimeName,
                task == null ? "N/A" : task.routingKey(),
                rejected);
    }

    /**
     * 기본 task 실패 핸들러입니다.
     *
     * @param task 실패 task
     * @param ex 처리 예외
     */
    private void defaultTaskFailureHandler(final T task, final Exception ex) {
        log.error("Mailbox task processing failed. runtime={}, routingKey={}",
                runtimeName,
                task == null ? "N/A" : task.routingKey(),
                ex);
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
     * 런타임 이름을 정규화합니다.
     *
     * @param runtimeName 입력 런타임 이름
     * @return 정규화된 이름
     */
    private static String normalizeRuntimeName(final String runtimeName) {
        if (runtimeName == null || runtimeName.isBlank()) {
            return "mailbox-runtime";
        }
        return runtimeName.trim();
    }

    /**
     * Mailbox 실행 런타임 옵션입니다.
     *
     * @param dispatcherThreads dispatcher 스레드 수
     * @param workerThreads worker 스레드 수(0 이하면 direct 모드)
     * @param shutdownWaitMs 종료 대기 시간(ms)
     * @param dispatcherThreadPrefix dispatcher 스레드 이름 prefix
     * @param workerThreadPrefix worker 스레드 이름 prefix
     * @param dispatcherRunnableDecorator dispatcher runnable 데코레이터
     * @param workerRunnableDecorator worker runnable 데코레이터
     */
    public record Config(
            int dispatcherThreads,
            int workerThreads,
            long shutdownWaitMs,
            String dispatcherThreadPrefix,
            String workerThreadPrefix,
            RunnableDecorator dispatcherRunnableDecorator,
            RunnableDecorator workerRunnableDecorator
    ) {

        /**
         * 입력 옵션을 검증하고 기본값을 보정합니다.
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
        }

        /**
         * worker pool 없이 direct 처리 모드 설정을 생성합니다.
         *
         * @param dispatcherThreads dispatcher 스레드 수
         * @param shutdownWaitMs 종료 대기 시간(ms)
         * @param dispatcherThreadPrefix dispatcher 스레드 prefix
         * @return direct 모드 설정
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
                    RunnableDecorator.identity()
            );
        }

        /**
         * worker pool 비동기 처리 모드 설정을 생성합니다.
         *
         * @param dispatcherThreads dispatcher 스레드 수
         * @param workerThreads worker 스레드 수
         * @param shutdownWaitMs 종료 대기 시간(ms)
         * @param dispatcherThreadPrefix dispatcher 스레드 prefix
         * @param workerThreadPrefix worker 스레드 prefix
         * @return 비동기 모드 설정
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
                    RunnableDecorator.identity()
            );
        }

        /**
         * worker pool 비동기 모드 여부를 반환합니다.
         *
         * @return workerThreads > 0이면 true
         */
        public boolean useWorkerPool() {
            return workerThreads > 0;
        }

        /**
         * dispatcher runnable 데코레이터를 교체한 새 설정을 반환합니다.
         *
         * @param decorator 적용할 데코레이터
         * @return 변경된 설정
         */
        public Config withDispatcherDecorator(final RunnableDecorator decorator) {
            return new Config(
                    dispatcherThreads,
                    workerThreads,
                    shutdownWaitMs,
                    dispatcherThreadPrefix,
                    workerThreadPrefix,
                    decorator,
                    workerRunnableDecorator
            );
        }

        /**
         * worker runnable 데코레이터를 교체한 새 설정을 반환합니다.
         *
         * @param decorator 적용할 데코레이터
         * @return 변경된 설정
         */
        public Config withWorkerDecorator(final RunnableDecorator decorator) {
            return new Config(
                    dispatcherThreads,
                    workerThreads,
                    shutdownWaitMs,
                    dispatcherThreadPrefix,
                    workerThreadPrefix,
                    dispatcherRunnableDecorator,
                    decorator
            );
        }
    }

    /**
     * task 처리 훅입니다.
     *
     * @param <T> task 타입
     */
    @FunctionalInterface
    public interface TaskProcessor<T extends MailboxTask> {

        /**
         * 단일 task를 처리합니다.
         *
         * @param task 처리 대상 task
         * @throws Exception 처리 실패 시 예외
         */
        void process(T task) throws Exception;
    }

    /**
     * worker queue 거부 훅입니다.
     *
     * @param <T> task 타입
     */
    @FunctionalInterface
    public interface TaskRejectedHandler<T extends MailboxTask> {

        /**
         * task 거부 시 호출됩니다.
         *
         * @param task 거부된 task
         * @param rejected 거부 예외
         */
        void onRejected(T task, RejectedExecutionException rejected);
    }

    /**
     * task 처리 실패 훅입니다.
     *
     * @param <T> task 타입
     */
    @FunctionalInterface
    public interface TaskFailureHandler<T extends MailboxTask> {

        /**
         * task 처리 실패 시 호출됩니다.
         *
         * @param task 실패 task
         * @param ex 처리 예외
         */
        void onTaskFailure(T task, Exception ex);
    }

    /**
     * 루프 예외 훅입니다.
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
     * runnable 데코레이터입니다.
     *
     * <p>MDC/GatewayLogContext 같은 컨텍스트 전파가 필요할 때 사용합니다.</p>
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
