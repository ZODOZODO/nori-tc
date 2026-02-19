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

    /**
     * isLoadOnStartup 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public boolean isLoadOnStartup() {
        return loadOnStartup;
    }

    /**
     * setLoadOnStartup 기능을 수행합니다.
     *
     * @param loadOnStartup 입력 값
     */

    public void setLoadOnStartup(final boolean loadOnStartup) {
        this.loadOnStartup = loadOnStartup;
    }

    /**
     * isFailFastOnStartup 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public boolean isFailFastOnStartup() {
        return failFastOnStartup;
    }

    /**
     * setFailFastOnStartup 기능을 수행합니다.
     *
     * @param failFastOnStartup 입력 값
     */

    public void setFailFastOnStartup(final boolean failFastOnStartup) {
        this.failFastOnStartup = failFastOnStartup;
    }

    /**
     * getPageSize 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getPageSize() {
        return pageSize;
    }

    /**
     * setPageSize 기능을 수행합니다.
     *
     * @param pageSize 입력 값
     */

    public void setPageSize(final int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * getMaxJarBytes 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getMaxJarBytes() {
        return maxJarBytes;
    }

    /**
     * setMaxJarBytes 기능을 수행합니다.
     *
     * @param maxJarBytes 입력 값
     */

    public void setMaxJarBytes(final long maxJarBytes) {
        this.maxJarBytes = maxJarBytes;
    }
}


