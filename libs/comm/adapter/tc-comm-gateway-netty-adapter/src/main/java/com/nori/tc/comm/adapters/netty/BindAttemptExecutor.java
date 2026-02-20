package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/**
 * UNBOUND 바인딩 파싱 작업을 비동기로 처리하는 전용 실행기입니다.
 *
 * <p>Netty I/O 이벤트 루프 스레드가 파싱/검증으로 오래 점유되지 않도록
 * 바인딩 시도는 별도 스레드 풀에서 처리합니다.</p>
 *
 * <p>호출 지점의 MDC를 보존해 EQP 로그 분리가 깨지지 않도록
 * 모든 작업은 {@link GatewayLogContext#wrap(Runnable)}로 감싼 뒤 실행합니다.</p>
 */
@Component
public class BindAttemptExecutor {

    private static final Logger log = LoggerFactory.getLogger(BindAttemptExecutor.class);

    /**
     * 바인딩 시도 전용 스레드 풀입니다.
     */
    private final ExecutorService executor;

    /**
     * 실행기 인스턴스에 할당된 스레드 수입니다.
     */
    private final int bindExecutorThreads;

    /**
     * 바인딩 시도 실행기를 초기화합니다.
     *
     * @param nettyProperties Netty 바인딩 실행 관련 설정
     */
    public BindAttemptExecutor(final GatewayNettyProperties nettyProperties) {
        final GatewayNettyProperties properties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.bindExecutorThreads = properties.getBindExecutorThreads();
        this.executor = Executors.newFixedThreadPool(bindExecutorThreads);
        log.info("BindAttemptExecutor initialized. threads={}", bindExecutorThreads);
    }

    /**
     * 바인딩 시도 작업을 비동기로 제출합니다.
     *
     * <p>작업 제출 시 호출 스레드의 MDC를 캡처하여 worker에서 복원합니다.</p>
     *
     * @param task 실행할 바인딩 시도 작업
     */
    public void submit(final Runnable task) {
        Objects.requireNonNull(task, "task is null");
        try {
            executor.execute(GatewayLogContext.wrap(task));
            if (log.isDebugEnabled()) {
                log.debug("Bind attempt task submitted. threads={}", bindExecutorThreads);
            }
        } catch (RejectedExecutionException ex) {
            log.warn("Bind attempt task rejected because executor is not accepting new tasks.");
            throw ex;
        }
    }

    /**
     * 실행기를 종료합니다.
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        log.info("BindAttemptExecutor shutdown requested.");
    }
}
