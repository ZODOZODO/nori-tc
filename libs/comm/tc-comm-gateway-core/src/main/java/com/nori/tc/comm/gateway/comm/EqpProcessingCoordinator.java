package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.usecase.EqpSequentialProcessor;
import com.nori.tc.comm.gateway.config.GatewayRuntimeProperties;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.common.mailbox.Mailbox;
import com.nori.tc.common.mailbox.MailboxScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 설비(eqpId) 단위 순차 처리를 담당하는 코디네이터입니다.
 *
 * <p>핵심 역할:</p>
 * <p>1) 공통 {@link MailboxScheduler}를 이용해 eqpId 단위 in-flight=1을 보장</p>
 * <p>2) inbound/outbound를 동일 worker 흐름에서 순차 처리</p>
 * <p>3) outbound 전송 실패 시 재시도/격리(quarantine) 정책 적용</p>
 *
 * <p>스케줄링 주의사항:</p>
 * <p>- 본 클래스는 "스케줄 토큰"만 공통 mailbox에 적재합니다.</p>
 * <p>- 실제 데이터(inbound/outbound)는 {@link EqpMailbox} 내부 큐에서 관리합니다.</p>
 */
@Service
public class EqpProcessingCoordinator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EqpProcessingCoordinator.class);

    /**
     * 스케줄 토큰 전용 mailbox 용량입니다.
     *
     * <p>동일 eqpId에 대해 "다음 처리 한 번"만 예약하면 충분하므로 1로 고정합니다.
     * 중복 schedule 요청은 자연스럽게 흡수(dedup)됩니다.</p>
     */
    private static final int SCHEDULER_MAILBOX_CAPACITY = 1;

    private final EqpMailboxRegistry mailboxRegistry;
    private final EqpSequentialProcessor sequentialProcessor;
    private final OutboundSenderPort outboundSenderPort;
    private final QuarantinePort quarantinePort;
    private final GatewayRuntimeProperties runtimeProperties;
    private final MailboxScheduler<EqpMailboxScheduleTask> mailboxScheduler =
            new MailboxScheduler<>(SCHEDULER_MAILBOX_CAPACITY);

    private ExecutorService workerPool;
    private ScheduledExecutorService retryScheduler;

    private volatile boolean running = false;

    /**
     * 코디네이터 의존성을 초기화합니다.
     *
     * @param mailboxRegistry eqpId별 mailbox 레지스트리
     * @param sequentialProcessor inbound 순차 처리기
     * @param outboundSenderPort outbound 송신 포트
     * @param quarantinePort 설비 격리 포트
     * @param runtimeProperties 게이트웨이 런타임 설정
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
    }

    /**
     * 특정 설비 mailbox에 대해 처리 스케줄을 등록합니다.
     *
     * <p>중복 스케줄은 공통 mailbox 용량(1) 정책으로 자동 흡수됩니다.</p>
     *
     * @param mailbox 처리 대상 설비 mailbox
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
            log.debug("설비 스케줄 토큰이 이미 대기 중이라 추가 등록을 생략합니다. eqpId={}", mailbox.eqpId());
        }
    }

    /**
     * 설비 언바인드 시 스케줄러 내부 상태를 정리합니다.
     *
     * @param eqpId 정리할 설비 ID
     */
    public void clearSchedulingState(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        mailboxScheduler.removeMailbox(eqpId);
        if (log.isDebugEnabled()) {
            log.debug("설비 스케줄링 상태를 정리했습니다. eqpId={}", eqpId);
        }
    }

    /**
     * worker/retry 스레드풀을 초기화하고 코디네이터를 기동합니다.
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        workerPool = Executors.newFixedThreadPool(runtimeProperties.getWorkerThreads());
        retryScheduler = Executors.newScheduledThreadPool(runtimeProperties.getOutboundRetrySchedulerThreads());

        for (int i = 0; i < runtimeProperties.getWorkerThreads(); i++) {
            // lifecycle 스레드의 로그 컨텍스트를 worker loop까지 보존합니다.
            workerPool.execute(GatewayLogContext.wrap(this::runWorkerLoop));
        }

        log.info("EqpProcessingCoordinator started. workerThreads={}, retrySchedulerThreads={}, schedulerMailboxCapacity={}",
                runtimeProperties.getWorkerThreads(),
                runtimeProperties.getOutboundRetrySchedulerThreads(),
                SCHEDULER_MAILBOX_CAPACITY);
    }

    /**
     * 코디네이터를 중지하고 내부 스레드풀을 종료합니다.
     */
    @Override
    public synchronized void stop() {
        running = false;

        if (workerPool != null) {
            workerPool.shutdownNow();
            workerPool = null;
        }
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
        return running;
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
     * worker loop 본문입니다.
     *
     * <p>공통 mailbox에서 ready eqpId를 꺼내 단건 처리하고,
     * 처리 후 공통 scheduler에 release 신호를 반환합니다.</p>
     */
    private void runWorkerLoop() {
        while (running) {
            try {
                final String eqpId = mailboxScheduler.takeReadyKey();
                final Mailbox<EqpMailboxScheduleTask> schedulingMailbox = mailboxScheduler.tryAcquire(eqpId);
                if (schedulingMailbox == null) {
                    continue;
                }

                final EqpMailboxScheduleTask scheduleTask = schedulingMailbox.poll();
                if (scheduleTask == null) {
                    mailboxScheduler.release(schedulingMailbox);
                    continue;
                }

                try {
                    processOnce(scheduleTask.eqpId());
                } finally {
                    mailboxScheduler.release(schedulingMailbox);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("Worker loop error.", ex);
            }
        }
    }

    /**
     * 단일 eqpId에 대한 한 번의 처리 사이클을 수행합니다.
     *
     * <p>처리 순서:</p>
     * <p>1) inbound 파이프라인 drain</p>
     * <p>2) outbound 큐 drain</p>
     * <p>3) 잔여 데이터가 있으면 다음 사이클을 재스케줄</p>
     *
     * @param eqpId 처리 대상 설비 ID
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
                log.debug("설비 큐 처리가 완료되었습니다. eqpId={}, inboundSize={}, outboundSize={}",
                        eqpId,
                        mailbox.inboundQueue().size(),
                        mailbox.outboundQueue().size());
            }
        }
    }

    /**
     * outbound 큐를 최대 {@code maxOutboundPerDrain}만큼 순차 전송합니다.
     *
     * @param mailbox 설비 mailbox
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
     * outbound 전송 실패 처리(재시도 또는 격리)를 수행합니다.
     *
     * @param mailbox 설비 mailbox
     * @param command 실패한 명령
     * @param ex 실패 예외
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

            retryScheduler.schedule(
                    GatewayLogContext.wrap(() -> {
                        final boolean offered = mailbox.outboundQueue().offer(retry);
                        if (!offered) {
                            log.warn("재시도 명령 enqueue에 실패하여 설비를 격리합니다. eqpId={}", mailbox.eqpId());
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

        log.warn("outbound 전송이 재시도 한도를 초과했습니다. eqpId={}, attempts={}",
                mailbox.eqpId(),
                command.attempt() + 1,
                ex);
        safeQuarantine(mailbox);
        safeClose(mailbox);
    }

    /**
     * 설비 채널을 안전하게 종료합니다.
     *
     * @param mailbox 설비 mailbox
     */
    private void safeClose(final EqpMailbox mailbox) {
        final EquipmentChannel channel = mailbox.channel();
        if (channel != null) {
            channel.close();
        }
    }

    /**
     * 설비를 quarantine 상태로 전환합니다.
     *
     * @param mailbox 설비 mailbox
     */
    private void safeQuarantine(final EqpMailbox mailbox) {
        try {
            quarantinePort.quarantine(
                    new EquipmentId(mailbox.eqpId()),
                    "OUTBOUND_SEND_FAILED",
                    "Outbound send failed"
            );
        } catch (Exception ignored) {
            // quarantine 포트 장애는 상위 처리 흐름을 추가로 깨지 않도록 로그만 남깁니다.
            log.debug("설비 quarantine 호출 중 예외가 발생했습니다. eqpId={}", mailbox.eqpId(), ignored);
        }
    }

    /**
     * inbound/outbound 큐에 잔여 데이터가 있는지 확인합니다.
     *
     * @param mailbox 설비 mailbox
     * @return 잔여 데이터가 있으면 true
     */
    private boolean hasPending(final EqpMailbox mailbox) {
        return mailbox.inboundQueue().size() > 0 || mailbox.outboundQueue().size() > 0;
    }

    /**
     * 현재 epoch millis를 반환합니다.
     *
     * @return 현재 시각(epoch millis)
     */
    private static long nowEpochMillis() {
        return System.currentTimeMillis();
    }
}
