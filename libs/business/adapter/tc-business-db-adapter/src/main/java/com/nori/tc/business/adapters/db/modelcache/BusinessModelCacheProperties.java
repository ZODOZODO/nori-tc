package com.nori.tc.business.adapters.db.modelcache;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tc-business-core-app model runtime cache 설정 프로퍼티입니다.
 *
 * <p>prefix: {@code tc.business.core.model-cache}</p>
 */
@ConfigurationProperties(prefix = "tc.business.core.model-cache")
public class BusinessModelCacheProperties {

    private static final Logger log = LoggerFactory.getLogger(BusinessModelCacheProperties.class);

    /**
     * 애플리케이션 기동 시 DB 스냅샷을 선로딩할지 여부입니다.
     */
    private Boolean loadOnStartup;

    /**
     * 선로딩 실패 시 기동을 중단할지 여부입니다.
     */
    private Boolean failFastOnStartup;

    /**
     * DB 페이지 조회 시 사용할 공통 페이지 크기입니다.
     */
    private Integer pageSize;

    /**
     * 설정 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        if (loadOnStartup == null) {
            throw new IllegalStateException("tc.business.core.model-cache.load-on-startup is required");
        }
        if (failFastOnStartup == null) {
            throw new IllegalStateException("tc.business.core.model-cache.fail-fast-on-startup is required");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new IllegalStateException("tc.business.core.model-cache.page-size must be > 0");
        }
        log.info("BusinessModelCacheProperties validated. loadOnStartup={}, failFastOnStartup={}, pageSize={}",
                loadOnStartup,
                failFastOnStartup,
                pageSize);
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
}

