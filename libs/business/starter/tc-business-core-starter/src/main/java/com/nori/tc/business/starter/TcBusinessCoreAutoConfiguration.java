package com.nori.tc.business.starter;

import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.logging.BusinessObservationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * tc-business-core starter auto configuration entrypoint.
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.nori.tc.business")
@Import({
        BusinessCoreRuntimeConfiguration.class,
        BusinessUiTaskPipelineConfiguration.class
})
public class TcBusinessCoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TcBusinessCoreAutoConfiguration.class);

    public TcBusinessCoreAutoConfiguration() {
        log.info("BIZ_BOOT_STARTING. imports=[BusinessCoreRuntimeConfiguration, BusinessUiTaskPipelineConfiguration]");
    }

    /**
     * Emits a compact business startup summary after the application is ready.
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> businessStartupReadyObservationListener(
            final BusinessCoreRuntimeProperties properties
    ) {
        return event -> {
            log.info("BIZ_BOOT_COMPONENT_READY. component=BusinessRuntime");
            log.info("BIZ_BOOT_COMPONENT_READY. component=KafkaTopics");

            final String appName = event.getApplicationContext()
                    .getEnvironment()
                    .getProperty("spring.application.name", "tc-business-core-app");
            final BusinessCoreRuntimeProperties.Kafka kafka = properties.getKafka();
            final BusinessCoreRuntimeProperties.Runtime runtime = properties.getRuntime();

            final String consumeTopics = String.join(",",
                    kafka.getEqpEventsTopic(),
                    kafka.getMesEventsTopic(),
                    kafka.getUiEventsTopic());
            final String produceTopics = String.join(",",
                    kafka.getEqpCommandsTopic(),
                    kafka.getMesCommandsTopic(),
                    kafka.getUiCommandsTopic());

            BusinessObservationLogger.logBootReady(
                    appName,
                    consumeTopics,
                    produceTopics,
                    kafka.getSource(),
                    runtime.getWorkerThreads(),
                    runtime.getTimeoutSchedulerThreads()
            );
        };
    }
}
