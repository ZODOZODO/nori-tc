package com.nori.tc.comm.adapters.plugin.socket;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway SOCKET 플러그인 런타임 설정 프로퍼티입니다.
 *
 * <p>prefix: {@code tc.comm.gateway.plugin-runtime}</p>
 *
 * <p>운영 제어 포인트:</p>
 * <p>1) 기동 시 preload 수행 여부</p>
 * <p>2) preload 실패 시 fail-fast 여부</p>
 * <p>3) preload 대상 설비 페이징 크기</p>
 * <p>4) 임시 JAR 저장 루트 디렉터리(옵션)</p>
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.plugin-runtime")
public class GatewaySocketPluginRuntimeProperties {

    /**
     * 프로퍼티 검증/초기화 로그입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewaySocketPluginRuntimeProperties.class);

    /**
     * 애플리케이션 기동 직후 tc_jar_gateway 기반 preload 수행 여부입니다.
     */
    private boolean loadOnStartup = true;

    /**
     * preload 실패 시 기동 자체를 중단할지 여부입니다.
     */
    private boolean failFastOnStartup = true;

    /**
     * preload 대상 eqp 조회 페이지 크기입니다.
     */
    private int pageSize = 500;

    /**
     * 플러그인 JAR 임시 저장 루트 디렉터리입니다.
     *
     * <p>null/blank 이면 기본 경로({@code java.io.tmpdir/nori-tc/comm-gateway-plugin-runtime})를 사용합니다.</p>
     */
    private String tempRootDir;

    /**
     * 프로퍼티 값을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        if (pageSize <= 0) {
            throw new IllegalStateException("tc.comm.gateway.plugin-runtime.page-size must be > 0");
        }
        log.info(
                "GatewaySocketPluginRuntimeProperties validated. loadOnStartup={}, failFastOnStartup={}, pageSize={}, tempRootDir={}",
                loadOnStartup,
                failFastOnStartup,
                pageSize,
                tempRootDir
        );
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

    public String getTempRootDir() {
        return tempRootDir;
    }

    public void setTempRootDir(final String tempRootDir) {
        this.tempRootDir = tempRootDir;
    }
}
