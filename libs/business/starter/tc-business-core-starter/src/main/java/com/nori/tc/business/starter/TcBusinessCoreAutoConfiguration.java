package com.nori.tc.business.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * tc-business-core starter 자동 구성 진입점입니다.
 *
 * <p>역할은 다음과 같습니다.</p>
 * <p>1) business 계층(core/adapter)의 컴포넌트를 스캔해 애플리케이션 컨텍스트에 등록합니다.</p>
 * <p>2) 런타임/파이프라인 관련 수동 설정 클래스를 함께 Import하여 조립을 완료합니다.</p>
 *
 * <p>주의 사항:</p>
 * <p>- app 모듈은 본 auto-configuration 하나만 의존하면 실행 조립이 가능해야 합니다.</p>
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.nori.tc.business")
@Import({
        BusinessCoreRuntimeConfiguration.class,
        BusinessUiTaskPipelineConfiguration.class
})
public class TcBusinessCoreAutoConfiguration {
}
