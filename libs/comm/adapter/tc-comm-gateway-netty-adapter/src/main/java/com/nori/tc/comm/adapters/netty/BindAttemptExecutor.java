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

/**
 * UNBOUND 상태의 바인딩 파싱을 위한 별도 실행기.
 */
@Component
public class BindAttemptExecutor {

    private static final Logger log = LoggerFactory.getLogger(BindAttemptExecutor.class);

    private final ExecutorService executor;

    
    /**
     * 게이트웨이 Netty 어댑터 구성 요소를 초기화합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param nettyProperties 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public BindAttemptExecutor(final GatewayNettyProperties nettyProperties) {
        // 연결 제어 단계: 상태 전이와 예외 케이스를 함께 관리합니다.
        Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.executor = Executors.newFixedThreadPool(nettyProperties.getBindExecutorThreads());
        log.info("BindAttemptExecutor initialized. threads={}", nettyProperties.getBindExecutorThreads());
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param task 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public void submit(final Runnable task) {
        // Bind job is executed on a separate pool, so capture/restore MDC.
        executor.execute(GatewayLogContext.wrap(task));
        if (log.isDebugEnabled()) {
            log.debug("Bind attempt task submitted.");
        }
    }

    
    /**
     * 게이트웨이 Netty 어댑터 리소스를 정리하고 종료합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        log.info("BindAttemptExecutor shutdown requested.");
    }
}
