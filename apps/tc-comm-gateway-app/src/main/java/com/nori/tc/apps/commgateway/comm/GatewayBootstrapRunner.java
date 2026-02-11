package com.nori.tc.apps.commgateway.comm;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 애플리케이션 구동 직후 실행되는 훅.
 *
 * - 현재는 no-op 이지만, 필요 시 초기화/검증 로직을 넣을 수 있다
 * - 코어 구성/어댑터 구성을 변경하지 않도록 앱 모듈에만 남긴다
 */

/**
 * 애플리케이션 시작 시 사전 준비 작업.
 *
 * - mailbox는 BOUND 이후 생성됩니다.
 * - active/passive 연결은 Netty bootstrap에서 수행됩니다.
 */
@Component
public class GatewayBootstrapRunner implements ApplicationRunner {

    @SuppressWarnings("unused")
    private final GatewayProcessingService processingService;

    public GatewayBootstrapRunner(final GatewayProcessingService processingService) {
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
    }

    @Override
    public void run(final ApplicationArguments args) {
        // no-op
    }
}
