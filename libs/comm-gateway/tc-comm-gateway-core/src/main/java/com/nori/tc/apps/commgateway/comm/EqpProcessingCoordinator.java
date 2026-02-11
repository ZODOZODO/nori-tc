package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.apps.commgateway.config.GatewayRuntimeProperties;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.usecase.EqpSequentialProcessor;
import com.nori.tc.apps.commgateway.metrics.GatewayLogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * eqp 단위 ReadyQueue + worker 풀 처리.
 *
 * - inbound/outbound 처리를 동일 스레드에서 직렬 실행
 * - scheduled 플래그로 중복 enqueue 방지
 * - inFlight 플래그로 동시 처리 방지
 */
@Service
public class EqpProcessingCoordinator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EqpProcessingCoordinator.class);

    private final EqpMailboxRegistry mailboxRegistry;
    private final EqpSequentialProcessor sequentialProcessor;
    private final OutboundSenderPort outboundSenderPort;
    private final QuarantinePort quarantinePort;
    private final GatewayRuntimeProperties runtimeProperties;

    private final ReadyQueue readyQueue = new ReadyQueue();
    private ExecutorService workerPool;
    private ScheduledExecutorService retryScheduler;

    private volatile boolean running = false;

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
     * ReadyQueue에 eqpId를 스케줄링합니다.
     */
    public void schedule(final EqpMailbox mailbox) {
        if (mailbox == null) {
            return;
        }
        if (!mailbox.scheduledFlag().compareAndSet(false, true)) {
            return;
        }
        readyQueue.offer(mailbox.eqpId());
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        workerPool = Executors.newFixedThreadPool(runtimeProperties.getWorkerThreads());
        retryScheduler = Executors.newScheduledThreadPool(runtimeProperties.getOutboundRetrySchedulerThreads());

        for (int i = 0; i < runtimeProperties.getWorkerThreads(); i++) {
            workerPool.execute(this::runWorkerLoop);
        }
        log.info("EqpProcessingCoordinator started with {} worker threads", runtimeProperties.getWorkerThreads());
    }

    @Override
    public void stop() {
        running = false;
        if (workerPool != null) {
            workerPool.shutdown();
        }
        if (retryScheduler != null) {
            retryScheduler.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private void runWorkerLoop() {
        while (running) {
            try {
                final String eqpId = readyQueue.take();
                processOnce(eqpId);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("Worker loop error", ex);
            }
        }
    }

    private void processOnce(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        // 설비별 로그 파일 분기를 위해 MDC에 eqpId를 설정한다.
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
            final EqpMailbox mailbox = mailboxRegistry.get(eqpId);
            if (mailbox == null) {
                return;
            }

            // allow re-schedule while processing
            mailbox.scheduledFlag().set(false);

            if (!mailbox.inFlightFlag().compareAndSet(false, true)) {
                return;
            }

            try {
                // Inbound pipeline (per-eqp, sequential):
                // - reassembly -> decode -> publish events
                sequentialProcessor.drain(mailbox.context());

                // Outbound pipeline (queue-based):
                // - drain outbound queue -> send in order
                drainOutbound(mailbox);
            } finally {
                mailbox.inFlightFlag().set(false);
            }

            if (hasPending(mailbox)) {
                schedule(mailbox);
            }
        }
    }

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

    private void handleOutboundFailure(
            final EqpMailbox mailbox,
            final OutboundCommand command,
            final Exception ex
    ) {
        final int maxRetry = runtimeProperties.getOutboundRetryMax();

        if (command.attempt() < maxRetry) {
            final OutboundCommand retry = command.nextAttempt();
            final int delayMs = runtimeProperties.getOutboundRetryBackoffMs();

            retryScheduler.schedule(() -> {
                final boolean offered = mailbox.outboundQueue().offer(retry);
                if (!offered) {
                    // queue overflow: close/quarantine
                    log.warn("Outbound queue overflow on retry. eqpId={}", mailbox.eqpId());
                    safeQuarantine(mailbox);
                    safeClose(mailbox);
                    return;
                }
                schedule(mailbox);
            }, delayMs, TimeUnit.MILLISECONDS);
            return;
        }

        log.warn("Outbound send failed (max retry exceeded). eqpId={}", mailbox.eqpId(), ex);
        safeQuarantine(mailbox);
        safeClose(mailbox);
    }

    private void safeClose(final EqpMailbox mailbox) {
        final EquipmentChannel channel = mailbox.channel();
        if (channel != null) {
            channel.close();
        }
    }

    private void safeQuarantine(final EqpMailbox mailbox) {
        try {
            quarantinePort.quarantine(new EquipmentId(mailbox.eqpId()), "OUTBOUND_SEND_FAILED", "Outbound send failed");
        } catch (Exception ignored) {
        }
    }

    private boolean hasPending(final EqpMailbox mailbox) {
        final int inboundSize = mailbox.inboundQueue().size();
        final int outboundSize = mailbox.outboundQueue().size();
        return inboundSize > 0 || outboundSize > 0;
    }
}
