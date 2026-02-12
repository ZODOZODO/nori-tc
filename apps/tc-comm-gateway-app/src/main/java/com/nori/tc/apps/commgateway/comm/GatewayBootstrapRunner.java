package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 애플리케이션 시작 직후 실행되는 부트스트랩 러너.
 *
 * <p>역할
 * - GatewayProcessingService 빈 주입 상태를 검증합니다.
 * - 초기화 지점에서 실행 인자와 부트스트랩 상태를 로그로 기록합니다.
 *
 * <p>확장 포인트
 * - 장비별 초기 동기화, 캐시 워밍업, 사전 점검 로직을 추가할 수 있습니다.
 */
@Component
public class GatewayBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GatewayBootstrapRunner.class);

    @SuppressWarnings("unused")
    private final GatewayProcessingService processingService;

    /**
     * 게이트웨이 애플리케이션 구성 요소를 초기화합니다.
     *
     * <p>애플리케이션 부트스트랩, 외부 설정 로딩, 런타임 초기화 흐름을 기준으로 동작합니다.</p>
     * @param processingService 게이트웨이 애플리케이션 처리에 사용하는 입력 값
     */
    public GatewayBootstrapRunner(final GatewayProcessingService processingService) {
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        log.info("GatewayBootstrapRunner 생성 완료. processingServiceType={}", this.processingService.getClass().getName());
    }

    /**
     * 게이트웨이 애플리케이션 실행 흐름을 처리합니다.
     *
     * <p>애플리케이션 부트스트랩, 외부 설정 로딩, 런타임 초기화 흐름을 기준으로 동작합니다.</p>
     * @param args 애플리케이션 실행 인자
     */
    @Override
    public void run(final ApplicationArguments args) {
        if (args == null) {
            log.warn("GatewayBootstrapRunner 실행. ApplicationArguments가 null이어서 기본 초기화만 수행합니다.");
            return;
        }

        log.info("GatewayBootstrapRunner 실행 시작. optionNames={}, nonOptionArgsCount={}",
                args.getOptionNames(), args.getNonOptionArgs().size());
        log.debug("GatewayProcessingService 주입 상태 확인. beanType={}", processingService.getClass().getName());
        log.info("GatewayBootstrapRunner 실행 완료.");
    }
}
