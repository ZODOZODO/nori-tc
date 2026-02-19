package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.usecase.EqpSequentialProcessor;
import com.nori.tc.comm.gateway.config.GatewayRuntimeProperties;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.common.mailbox.MailboxScheduler;
import com.nori.tc.common.mailbox.execution.MailboxExecutionRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 장비(EQP) 단위 순차 처리 코디네이터입니다.
 *
 * <p>핵심 역할은 다음과 같습니다.</p>
 * <p>1) 공통 {@link MailboxScheduler}로 eqpId 단위 in-flight=1 보장</p>
 * <p>2) 공통 {@link MailboxExecutionRuntime}로 dispatcher 루프 실행</p>
 * <p>3) 장비 inbound/outbound를 한 사이클씩 처리하고 필요 시 재스케줄</p>
 * <p>4) outbound 실패 시 재시도 또는 quarantine 정책 적용</p>
 */
@Service
public class EqpProcessingCoordinator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EqpProcessingCoordinator.class);

    /**
     * 스케줄 토큰 전용 mailbox 용량입니다.
     *
     * <p>eqpId당 "처리 필요" 토큰만 유지하면 충분하므로 1로 고정합니다.</p>
     */
    private static final int SCHEDULER_MAILBOX_CAPACITY = 1;

    /**
     * 공통 mailbox 런타임 종료 대기 시간(ms)입니다.
     */
    private static final long MAILBOX_RUNTIME_SHUTDOWN_WAIT_MS = 3_000L;

    private final EqpMailboxRegistry mailboxRegistry;
    private final EqpSequentialProcessor sequentialProcessor;
    private final OutboundSenderPort outboundSenderPort;
    private final QuarantinePort quarantinePort;
    private final GatewayRuntimeProperties runtimeProperties;
    private final MailboxScheduler<EqpMailboxScheduleTask> mailboxScheduler =
            new MailboxScheduler<>(SCHEDULER_MAILBOX_CAPACITY);
    private final MailboxExecutionRuntime<EqpMailboxScheduleTask> mailboxExecutionRuntime;

    private ScheduledExecutorService retryScheduler;

    private volatile boolean running = false;

    /**
     * 코디네이터 의존성을 초기화합니다.
     *
     * @param mailboxRegistry eqpId별 mailbox 레지스트리
     * @param sequentialProcessor inbound 순차 처리기
     * @param outboundSenderPort outbound 송신 포트
     * @param quarantinePort 장비 격리 포트
     * @param runtimeProperties 런타임 설정
     */
    public EqpProcessingCoordinator(
            final EqpMailboxRegistry mailboxRegistry,
            final EqpSequentialProcessor sequentialProcessor,
            final OutboundSenderPort outboundSenderPort,
            final QuarantinePort quarantinePort,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        this.mailboxRegistry = Objects.requireNonNull(mailboxRegistry, "mailboxRegistry is null");
        this.sequentialProcessor = Objects.requireNonNull(sequentialProcessor, "sequentialProcessor is null");
        this.outboundSenderPort = Objects.requireNonNull(outboundSenderPort, "outboundSenderPort is null");
        this.quarantinePort = Objects.requireNonNull(quarantinePort, "quarantinePort is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");

        final MailboxExecutionRuntime.Config runtimeConfig = MailboxExecutionRuntime.Config
                .direct(
                        runtimeProperties.getWorkerThreads(),
                        MAILBOX_RUNTIME_SHUTDOWN_WAIT_MS,
                        "gateway-eqp-dispatcher-"
                )
                .withDispatcherDecorator(GatewayLogContext::wrap);

        this.mailboxExecutionRuntime = new MailboxExecutionRuntime<>(
                "gateway-eqp-coordinator",
                mailboxScheduler,
                runtimeConfig,
                this::handleScheduledMailboxTask,
                null,
                (task, ex) -> log.warn("장비 task 처리 중 예외가 발생했습니다. eqpId={}", task.eqpId(), ex),
                ex -> log.warn("장비 mailbox dispatcher 루프에서 예외가 발생했습니다.", ex)
        );
    }

    /**
     * 특정 장비 mailbox를 실행 큐에 등록합니다.
     *
     * <p>중복 스케줄 요청은 scheduler 내부 dedup 플래그로 자동 흡수됩니다.</p>
     *
     * @param mailbox 처리 대상 장비 mailbox
     */
    public void schedule(final EqpMailbox mailbox) {
        if (mailbox == null) {
            return;
        }

        final long now = nowEpochMillis();
        final boolean offered = mailboxScheduler.enqueue(
                new EqpMailboxScheduleTask(mailbox.eqpId(), now),
                now
        );
        if (!offered && log.isDebugEnabled()) {
            log.debug("장비 스케줄 토큰이 이미 대기 중이어서 추가 등록을 생략합니다. eqpId={}", mailbox.eqpId());
        }
    }

    /**
     * 장비 삭제/언바인드 시 scheduler 내부 상태를 정리합니다.
     *
     * @param eqpId 정리할 장비 ID
     */
    public void clearSchedulingState(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        mailboxScheduler.removeMailbox(eqpId);
        if (log.isDebugEnabled()) {
            log.debug("장비 스케줄 상태를 정리했습니다. eqpId={}", eqpId);
        }
    }

    /**
     * 코디네이터를 시작합니다.
     *
     * <p>공통 mailbox 실행 런타임과 outbound 재시도 스케줄러를 함께 기동합니다.</p>
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        retryScheduler = Executors.newScheduledThreadPool(runtimeProperties.getOutboundRetrySchedulerThreads());
        mailboxExecutionRuntime.start();

        log.info("EqpProcessingCoordinator started. dispatcherThreads={}, retrySchedulerThreads={}, schedulerMailboxCapacity={}",
                runtimeProperties.getWorkerThreads(),
                runtimeProperties.getOutboundRetrySchedulerThreads(),
                SCHEDULER_MAILBOX_CAPACITY);
    }

    /**
     * 코디네이터를 중지합니다.
     */
    @Override
    public synchronized void stop() {
        running = false;

        mailboxExecutionRuntime.stop();
        if (retryScheduler != null) {
            retryScheduler.shutdownNow();
            retryScheduler = null;
        }

        log.info("EqpProcessingCoordinator stopped.");
    }

    /**
     * 현재 실행 상태를 반환합니다.
     *
     * @return 실행 중이면 true
     */
    @Override
    public boolean isRunning() {
        return running && mailboxExecutionRuntime.isRunning();
    }

    /**
     * Spring lifecycle phase를 반환합니다.
     *
     * @return phase 값
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 공통 런타임에서 전달한 스케줄 task를 처리합니다.
     *
     * @param scheduleTask 처리 대상 task
     */
    private void handleScheduledMailboxTask(final EqpMailboxScheduleTask scheduleTask) {
        if (scheduleTask == null) {
            return;
        }
        processOnce(scheduleTask.eqpId());
    }

    /**
     * 단일 eqpId에 대해 한 사이클의 처리를 수행합니다.
     *
     * <p>처리 순서:</p>
     * <p>1) inbound drain</p>
     * <p>2) outbound drain</p>
     * <p>3) 잔여 데이터가 있으면 다음 사이클 재스케줄</p>
     *
     * @param eqpId 처리 대상 장비 ID
     */
    private void processOnce(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
            final EqpMailbox mailbox = mailboxRegistry.get(eqpId);
            if (mailbox == null) {
                if (log.isDebugEnabled()) {
                    log.debug("처리 대상 mailbox가 존재하지 않아 스킵합니다. eqpId={}", eqpId);
                }
                return;
            }

            sequentialProcessor.drain(mailbox.context());
            drainOutbound(mailbox);

            if (hasPending(mailbox)) {
                schedule(mailbox);
            } else if (log.isDebugEnabled()) {
                log.debug("장비 한 사이클 처리가 완료되었습니다. eqpId={}, inboundSize={}, outboundSize={}",
                        eqpId,
                        mailbox.inboundQueue().size(),
                        mailbox.outboundQueue().size());
            }
        }
    }

    /**
     * outbound 큐를 최대 {@code maxOutboundPerDrain} 개수까지 순차 송신합니다.
     *
     * @param mailbox 장비 mailbox
     */
    private void drainOutbound(final EqpMailbox mailbox) {
        int processed = 0;
        final int maxOutbound = runtimeProperties.getMaxOutboundPerDrain();

        while (processed < maxOutbound) {
            final OutboundCommand command = mailbox.outboundQueue().poll();
            if (command == null) {
                return;
            }

            processed++;
            try {
                outboundSenderPort.send(command.frame());
            } catch (Exception ex) {
                handleOutboundFailure(mailbox, command, ex);
                return;
            }
        }
    }

    /**
     * outbound 송신 실패를 처리합니다.
     *
     * <p>정책:</p>
     * <p>1) 재시도 한도 이내면 지연 후 재큐잉</p>
     * <p>2) 한도 초과면 장비를 quarantine 처리하고 채널 종료</p>
     *
     * @param mailbox 장비 mailbox
     * @param command 실패한 명령
     * @param ex 송신 예외
     */
    private void handleOutboundFailure(
            final EqpMailbox mailbox,
            final OutboundCommand command,
            final Exception ex
    ) {
        final int maxRetry = runtimeProperties.getOutboundRetryMax();
        if (command.attempt() < maxRetry) {
            final OutboundCommand retry = command.nextAttempt();
            final int delayMs = runtimeProperties.getOutboundRetryBackoffMs();

            final ScheduledExecutorService scheduler = retryScheduler;
            if (scheduler == null) {
                log.warn("재시도 스케줄러가 종료되어 명령을 재시도할 수 없습니다. eqpId={}", mailbox.eqpId());
                safeQuarantine(mailbox);
                safeClose(mailbox);
                return;
            }

            scheduler.schedule(
                    GatewayLogContext.wrap(() -> {
                        final boolean offered = mailbox.outboundQueue().offer(retry);
                        if (!offered) {
                            log.warn("재시도 명령 enqueue가 실패하여 장비를 격리합니다. eqpId={}", mailbox.eqpId());
                            safeQuarantine(mailbox);
                            safeClose(mailbox);
                            return;
                        }
                        schedule(mailbox);
                    }),
                    delayMs,
                    TimeUnit.MILLISECONDS
            );
            return;
        }

        log.warn("outbound 송신 재시도 한도를 초과했습니다. eqpId={}, attempts={}",
                mailbox.eqpId(),
                command.attempt() + 1,
                ex);
        safeQuarantine(mailbox);
        safeClose(mailbox);
    }

    /**
     * 장비 채널을 안전하게 종료합니다.
     *
     * @param mailbox 장비 mailbox
     */
    private void safeClose(final EqpMailbox mailbox) {
        final EquipmentChannel channel = mailbox.channel();
        if (channel != null) {
            channel.close();
        }
    }

    /**
     * 장비를 quarantine 상태로 전환합니다.
     *
     * @param mailbox 장비 mailbox
     */
    private void safeQuarantine(final EqpMailbox mailbox) {
        try {
            quarantinePort.quarantine(
                    new EquipmentId(mailbox.eqpId()),
                    "OUTBOUND_SEND_FAILED",
                    "Outbound send failed"
            );
        } catch (Exception ignored) {
            // quarantine 실패는 보조 경로이므로 처리 흐름을 중단하지 않습니다.
            log.debug("장비 quarantine 호출 중 예외가 발생했습니다. eqpId={}", mailbox.eqpId(), ignored);
        }
    }

    /**
     * inbound/outbound 큐에 잔여 데이터가 있는지 반환합니다.
     *
     * @param mailbox 장비 mailbox
     * @return 잔여 데이터가 있으면 true
     */
    private boolean hasPending(final EqpMailbox mailbox) {
        return mailbox.inboundQueue().size() > 0 || mailbox.outboundQueue().size() > 0;
    }

    /**
     * 현재 epoch millis를 반환합니다.
     *
     * @return 현재 시간(epoch millis)
     */
    private static long nowEpochMillis() {
        return System.currentTimeMillis();
    }
}
