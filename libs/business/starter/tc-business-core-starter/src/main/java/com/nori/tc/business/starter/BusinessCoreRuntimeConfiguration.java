package com.nori.tc.business.starter;

import com.nori.tc.business.adapters.db.modelcache.BusinessModelCacheProperties;
import com.nori.tc.business.adapters.plugin.workflow.BusinessWorkflowPluginRuntimeProperties;
import com.nori.tc.business.adapters.redis.dlq.BusinessRedisProperties;
import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.dlq.BusinessDlqPublisherPort;
import com.nori.tc.business.core.messaging.BusinessEqpCommandPublishPort;
import com.nori.tc.business.core.messaging.BusinessMesCommandPublishPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Business Core 런타임 공통 설정 구성 클래스입니다.
 */
@Configuration
@EnableConfigurationProperties({
        BusinessCoreRuntimeProperties.class,
        BusinessModelCacheProperties.class,
        BusinessWorkflowPluginRuntimeProperties.class,
        BusinessRedisProperties.class
})
/**
 * BusinessCoreRuntimeConfiguration 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */

public class BusinessCoreRuntimeConfiguration {

    /**
     * DLQ 발행 포트 기본 구현(no-op)을 등록합니다.
     *
     * <p>운영 환경에서 Redis/DB/Kafka 등의 실제 DLQ 어댑터가 존재하면
     * 해당 구현이 우선되고 이 기본 빈은 생성되지 않습니다.</p>
     *
     * @return no-op DLQ 포트
     */
    @Bean
    @ConditionalOnMissingBean(BusinessDlqPublisherPort.class)
    public BusinessDlqPublisherPort businessDlqPublisherPort() {
        return BusinessDlqPublisherPort.noop();
    }

    /**
     * EQP 명령 발행 포트 기본 구현(no-op)을 등록합니다.
     *
     * <p>Kafka 어댑터가 없더라도 코어 런타임이 안전하게 기동될 수 있도록
     * 기본 no-op 구현을 제공합니다.</p>
     *
     * @return no-op EQP 명령 발행 포트
     */
    @Bean
    @ConditionalOnMissingBean(BusinessEqpCommandPublishPort.class)
    public BusinessEqpCommandPublishPort businessEqpCommandPublishPort() {
        return BusinessEqpCommandPublishPort.noop();
    }

    /**
     * MES 명령 발행 포트 기본 구현(no-op)을 등록합니다.
     *
     * <p>Kafka 어댑터가 없더라도 코어 런타임이 안전하게 기동될 수 있도록
     * 기본 no-op 구현을 제공합니다.</p>
     *
     * @return no-op MES 명령 발행 포트
     */
    @Bean
    @ConditionalOnMissingBean(BusinessMesCommandPublishPort.class)
    public BusinessMesCommandPublishPort businessMesCommandPublishPort() {
        return BusinessMesCommandPublishPort.noop();
    }
}
