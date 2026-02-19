package com.nori.tc.business.adapters.plugin.workflow;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * workflow plugin runtime 설정 프로퍼티입니다.
 *
 * <p>prefix: {@code tc.business.core.plugin-runtime}</p>
 */
@ConfigurationProperties(prefix = "tc.business.core.plugin-runtime")
public class BusinessWorkflowPluginRuntimeProperties {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowPluginRuntimeProperties.class);

    /**
     * 애플리케이션 기동 시 tc_jar_business 기반 plugin runtime preload 수행 여부입니다.
     */
    private Boolean loadOnStartup;

    /**
     * preload 실패 시 기동을 중단할지 여부입니다.
     */
    private Boolean failFastOnStartup;

    /**
     * preload 시 eqp 목록 조회에 사용할 페이지 크기입니다.
     */
    private Integer pageSize;

    /**
     * 플러그인 JAR 최대 허용 크기(바이트)입니다.
     *
     * <p>메모리 급증/비정상 대용량 바이너리 로딩을 방지하기 위한
     * 1차 방어 설정입니다.</p>
     */
    private Long maxJarBytes;

    /**
     * 프로퍼티 유효성 검증을 수행합니다.
     */
    @PostConstruct
    public void validate() {
        if (loadOnStartup == null) {
            throw new IllegalStateException("tc.business.core.plugin-runtime.load-on-startup is required");
        }
        if (failFastOnStartup == null) {
            throw new IllegalStateException("tc.business.core.plugin-runtime.fail-fast-on-startup is required");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new IllegalStateException("tc.business.core.plugin-runtime.page-size must be > 0");
        }
        if (maxJarBytes == null || maxJarBytes <= 0L) {
            throw new IllegalStateException("tc.business.core.plugin-runtime.max-jar-bytes must be > 0");
        }
        log.info("BusinessWorkflowPluginRuntimeProperties validated. loadOnStartup={}, failFastOnStartup={}, pageSize={}, maxJarBytes={}",
                loadOnStartup,
                failFastOnStartup,
                pageSize,
                maxJarBytes);
    }

    public boolean isLoadOnStartup() {
        return loadOnStartup;
    }

    public void setLoadOnStartup(final boolean loadOnStartup) {
        this.loadOnStartup = loadOnStartup;
    }

    public boolean isFailFastOnStartup() {
        return failFastOnStartup;
    }

    public void setFailFastOnStartup(final boolean failFastOnStartup) {
        this.failFastOnStartup = failFastOnStartup;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(final int pageSize) {
        this.pageSize = pageSize;
    }

    public long getMaxJarBytes() {
        return maxJarBytes;
    }

    public void setMaxJarBytes(final long maxJarBytes) {
        this.maxJarBytes = maxJarBytes;
    }
}


