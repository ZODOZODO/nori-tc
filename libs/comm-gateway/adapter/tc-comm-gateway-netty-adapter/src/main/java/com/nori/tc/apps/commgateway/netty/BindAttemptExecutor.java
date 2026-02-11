package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.apps.commgateway.config.GatewayNettyProperties;
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

    public BindAttemptExecutor(final GatewayNettyProperties nettyProperties) {
        Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.executor = Executors.newFixedThreadPool(nettyProperties.getBindExecutorThreads());
        log.info("BindAttemptExecutor initialized. threads={}", nettyProperties.getBindExecutorThreads());
    }

    public void submit(final Runnable task) {
        executor.execute(task);
        if (log.isDebugEnabled()) {
            log.debug("Bind attempt task submitted.");
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        log.info("BindAttemptExecutor shutdown requested.");
    }
}
