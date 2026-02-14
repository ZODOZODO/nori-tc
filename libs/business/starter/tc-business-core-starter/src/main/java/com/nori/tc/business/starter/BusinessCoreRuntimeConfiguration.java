package com.nori.tc.business.starter;

import com.nori.tc.business.adapters.db.modelcache.BusinessModelCacheProperties;
import com.nori.tc.business.adapters.redis.dlq.BusinessRedisProperties;
import com.nori.tc.business.adapters.plugin.workflow.BusinessWorkflowPluginRuntimeProperties;
import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.dlq.BusinessDlqPublisherPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Business Core 런타임 설정 구성 클래스입니다.
 */
@Configuration
@EnableConfigurationProperties({
        BusinessCoreRuntimeProperties.class,
        BusinessModelCacheProperties.class,
        BusinessWorkflowPluginRuntimeProperties.class,
        BusinessRedisProperties.class
})
public class BusinessCoreRuntimeConfiguration {

    /**
     * DLQ 발행 포트 기본 구현(no-op)을 등록합니다.
     *
     * <p>운영 환경에서 Redis/DB/Kafka 등 실제 DLQ 어댑터 빈이 존재하면
     * 본 기본 빈은 생성되지 않습니다.</p>
     *
     * @return no-op DLQ 포트
     */
    @Bean
    @ConditionalOnMissingBean(BusinessDlqPublisherPort.class)
    public BusinessDlqPublisherPort businessDlqPublisherPort() {
        return BusinessDlqPublisherPort.noop();
    }
}


