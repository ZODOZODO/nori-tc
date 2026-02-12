package com.nori.tc.apps.commgateway.starter;

import com.nori.tc.apps.commgateway.config.GatewayCommConfiguration;
import com.nori.tc.apps.commgateway.config.GatewayProcessingConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * tc-comm-gateway 자동 구성.
 *
 * - 앱 모듈은 이 스타터만 의존하면 된다
 * - 내부에서 핵심 Bean 구성 클래스들을 Import 한다
 */
@AutoConfiguration
@Import({
        GatewayCommConfiguration.class,
        GatewayProcessingConfiguration.class
})
public class TcCommGatewayAutoConfiguration {
    // 자동 구성 전용 클래스 (내용 없음)
}
