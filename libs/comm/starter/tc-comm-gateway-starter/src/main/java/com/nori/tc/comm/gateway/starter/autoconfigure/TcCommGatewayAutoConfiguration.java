package com.nori.tc.comm.gateway.starter.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * `tc-comm-gateway` 스타터 자동구성 진입점입니다.
 *
 * <p>이 클래스는 Spring Boot 자동구성 단계에서 게이트웨이 구성의 시작점을 담당합니다.</p>
 * <p>주요 책임은 다음과 같습니다.</p>
 * <p>1) 공통 컴포넌트 스캔 범위를 지정합니다.</p>
 * <p>2) 게이트웨이 공통 Bean 구성과 처리 파이프라인 구성을 {@code @Import}로 결합합니다.</p>
 * <p>3) 스타터 로딩 여부를 초기화 로그로 남겨 운영 시점 진단을 돕습니다.</p>
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.nori.tc.comm")
@Import({
        GatewayCommConfiguration.class,
        GatewayProcessingConfiguration.class
})
public class TcCommGatewayAutoConfiguration {

    /**
     * 자동구성 로딩 여부 추적용 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(TcCommGatewayAutoConfiguration.class);

    /**
     * 자동구성 클래스 생성자입니다.
     *
     * <p>실제 Bean 조립은 {@code @Import}된 구성 클래스에서 수행되며,
     * 여기서는 자동구성이 로딩되었음을 식별하기 위한 최소 로그만 남깁니다.</p>
     */
    public TcCommGatewayAutoConfiguration() {
        if (log.isDebugEnabled()) {
            log.debug("TcCommGatewayAutoConfiguration 로딩됨. imports=[GatewayCommConfiguration, GatewayProcessingConfiguration]");
        }
    }
}
