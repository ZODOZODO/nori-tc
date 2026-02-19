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
/**
 * TcCommGatewayAutoConfiguration 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */

public class TcCommGatewayAutoConfiguration {
}
