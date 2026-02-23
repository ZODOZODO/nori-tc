package com.nori.tc.comm.gateway.config.props;

import com.nori.tc.comm.core.routing.PublishMode;
import com.nori.tc.comm.core.routing.spec.MessageMatchType;
import com.nori.tc.comm.core.routing.spec.PublishPolicyRule;
import com.nori.tc.comm.core.routing.spec.PublishPolicySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Publish policy configuration for OUTBOX vs DIRECT_KAFKA.
 *
 * - Properties are mapped to a PublishPolicySpec used by the engine.
 * - updatedAtEpochMs is required by the spec for hot-reload comparisons.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.publish-policy")
public class GatewayPublishPolicyProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayPublishPolicyProperties.class);

    private String version;

    /**
     * Timestamp used by PublishPolicySpec.
     * - Must be provided via properties or DB-backed refresh.
     */
    private Long updatedAtEpochMs;

    private PublishMode defaultMode;

    private List<Rule> rules;

    
    /**
     * 게이트웨이 코어 모듈 입력/설정 유효성을 검증합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    @PostConstruct
    public void validate() {
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("tc.comm.gateway.publish-policy.version is required");
        }
        if (defaultMode == null) {
            throw new IllegalStateException("tc.comm.gateway.publish-policy.default-mode is required");
        }
        if (updatedAtEpochMs == null || updatedAtEpochMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.publish-policy.updated-at-epoch-ms must be > 0");
        }
        if (rules != null) {
            for (Rule rule : rules) {
                if (rule == null) {
                    throw new IllegalStateException("publish-policy.rules entry must not be null");
                }
                if (rule.matchType == null) {
                    throw new IllegalStateException("publish-policy.rules.match-type is required");
                }
                if (rule.pattern == null || rule.pattern.isBlank()) {
                    throw new IllegalStateException("publish-policy.rules.pattern is required");
                }
                if (rule.publishMode == null) {
                    throw new IllegalStateException("publish-policy.rules.publish-mode is required");
                }
            }
        }
        log.info("GatewayPublishPolicyProperties validated. version={}, defaultMode={}, ruleCount={}",
                version, defaultMode, rules == null ? 0 : rules.size());
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public String getVersion() {
        return version;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param version 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setVersion(final String version) {
        this.version = version;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public PublishMode getDefaultMode() {
        return defaultMode;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param defaultMode 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setDefaultMode(final PublishMode defaultMode) {
        this.defaultMode = defaultMode;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param updatedAtEpochMs 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setUpdatedAtEpochMs(final long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 조회/처리 결과 목록
     */
    public List<Rule> getRules() {
        return rules;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param rules 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setRules(final List<Rule> rules) {
        this.rules = rules;
    }

    
    /**
     * 게이트웨이 코어 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public PublishPolicySpec toSpec() {
        final List<PublishPolicyRule> mappedRules = (rules == null ? List.<Rule>of() : rules).stream()
                .map(rule -> new PublishPolicyRule(
                        rule.matchType,
                        rule.pattern,
                        rule.publishMode,
                        rule.topic,
                        rule.key,
                        rule.headers == null ? Map.of() : rule.headers
                ))
                .toList();

        return new PublishPolicySpec(
                version,
                updatedAtEpochMs,
                defaultMode,
                mappedRules
        );
    }

    public static final class Rule {
        private MessageMatchType matchType;
        private String pattern;
        private PublishMode publishMode;
        private String topic;
        private String key;
        private Map<String, String> headers;

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public MessageMatchType getMatchType() {
            return matchType;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param matchType 게이트웨이 코어 모듈 처리에 사용하는 입력 값
         */
        public void setMatchType(final MessageMatchType matchType) {
            this.matchType = matchType;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public String getPattern() {
            return pattern;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param pattern 게이트웨이 코어 모듈 처리에 사용하는 입력 값
         */
        public void setPattern(final String pattern) {
            this.pattern = pattern;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public PublishMode getPublishMode() {
            return publishMode;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param publishMode 게이트웨이 코어 모듈 처리에 사용하는 입력 값
         */
        public void setPublishMode(final PublishMode publishMode) {
            this.publishMode = publishMode;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public String getTopic() {
            return topic;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param topic Kafka 토픽 이름
         */
        public void setTopic(final String topic) {
            this.topic = topic;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public String getKey() {
            return key;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param key 대상 키 값
         */
        public void setKey(final String key) {
            this.key = key;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public Map<String, String> getHeaders() {
            return headers;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param headers 게이트웨이 코어 모듈 처리에 사용하는 입력 값
         */
        public void setHeaders(final Map<String, String> headers) {
            this.headers = headers;
        }
    }
}
