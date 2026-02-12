package com.nori.tc.comm.gateway.starter;

import com.nori.tc.comm.gateway.config.GatewayCommConfiguration;
import com.nori.tc.comm.gateway.config.GatewayProcessingConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * tc-comm-gateway starter 자동 구성 진입점입니다.
 *
 * <p>패키지 스캔 범위를 {@code com.nori.tc.comm}로 지정해
 * gateway core + adapter 계층 컴포넌트가 함께 등록되도록 구성합니다.</p>
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.nori.tc.comm")
@Import({
        GatewayCommConfiguration.class,
        GatewayProcessingConfiguration.class
})
public class TcCommGatewayAutoConfiguration {
}
