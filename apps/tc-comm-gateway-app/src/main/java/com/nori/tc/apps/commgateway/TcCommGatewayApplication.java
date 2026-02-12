package com.nori.tc.apps.commgateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * tc-comm-gateway-app 진입점.
 *
 * <p>역할
 * - Spring Boot 애플리케이션 컨텍스트를 기동합니다.
 * - 시작/완료 로그를 남겨 기동 상태를 빠르게 확인할 수 있게 합니다.
 *
 * <p>참고
 * - 외부 설정은 application.yaml의 spring.config.import 경로를 통해 로딩됩니다.
 */
@SpringBootApplication
public class TcCommGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(TcCommGatewayApplication.class);

    /**
     * 게이트웨이 애플리케이션 도메인 처리 로직을 수행합니다.
     *
     * <p>애플리케이션 부트스트랩, 외부 설정 로딩, 런타임 초기화 흐름을 기준으로 동작합니다.</p>
     * @param args 애플리케이션 실행 인자
     */
    public static void main(String[] args) {
        int argsCount = args == null ? 0 : args.length;
        log.info("tc-comm-gateway-app 기동 시작. argsCount={}", argsCount);

        ConfigurableApplicationContext context = SpringApplication.run(TcCommGatewayApplication.class, args);

        log.info("tc-comm-gateway-app 기동 완료. beanDefinitionCount={}", context.getBeanDefinitionCount());
    }
}
