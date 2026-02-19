package com.nori.tc.comm.adapters.plugin.socket;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway SOCKET 플러그인 런타임 정책 속성입니다.
 *
 * <p>prefix: {@code tc.comm.gateway.plugin-runtime}</p>
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.plugin-runtime")
public class GatewaySocketPluginRuntimeProperties {

    /**
     * 정책 검증 결과를 초기 로그로 남깁니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewaySocketPluginRuntimeProperties.class);

    /**
     * 애플리케이션 기동 직후 tc_jar_gateway 기반 preload를 수행할지 여부입니다.
     */
    private Boolean loadOnStartup;

    /**
     * preload 실패 시 애플리케이션 기동 자체를 실패 처리할지 여부입니다.
     */
    private Boolean failFastOnStartup;

    /**
     * preload 시 tc_eqp 조회 페이지 크기입니다.
     */
    private Integer pageSize;

    /**
     * 플러그인 JAR 임시 파일 루트 디렉터리입니다.
     *
     * <p>null/blank면 기본 경로({@code java.io.tmpdir/nori-tc/comm-gateway-plugin-runtime})를 사용합니다.</p>
     */
    private String tempRootDir;

    /**
     * 플러그인 JAR 최대 허용 크기(byte)입니다.
     *
     * <p>과도하게 큰 JAR 로딩을 사전에 차단해 메모리/디스크 사용량을 보호합니다.</p>
     */
    private Long maxJarBytes;

    /**
     * 속성값 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        if (loadOnStartup == null) {
            throw new IllegalStateException("tc.comm.gateway.plugin-runtime.load-on-startup is required");
        }
        if (failFastOnStartup == null) {
            throw new IllegalStateException("tc.comm.gateway.plugin-runtime.fail-fast-on-startup is required");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new IllegalStateException("tc.comm.gateway.plugin-runtime.page-size must be > 0");
        }
        if (maxJarBytes == null || maxJarBytes <= 0L) {
            throw new IllegalStateException("tc.comm.gateway.plugin-runtime.max-jar-bytes must be > 0");
        }
        log.info(
                "GatewaySocketPluginRuntimeProperties validated. loadOnStartup={}, failFastOnStartup={}, pageSize={}, tempRootDir={}, maxJarBytes={}",
                loadOnStartup,
                failFastOnStartup,
                pageSize,
                tempRootDir,
                maxJarBytes
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

    public long getMaxJarBytes() {
        return maxJarBytes;
    }

    public void setMaxJarBytes(final long maxJarBytes) {
        this.maxJarBytes = maxJarBytes;
    }
}
