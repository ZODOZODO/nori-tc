package com.nori.tc.apps.commgateway.config;

import com.nori.tc.comm.core.routing.PublishMode;
import com.nori.tc.comm.core.routing.spec.MessageMatchType;
import com.nori.tc.comm.core.routing.spec.PublishPolicyRule;
import com.nori.tc.comm.core.routing.spec.PublishPolicySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OUTBOX vs DIRECT_KAFKA 라우팅 정책 설정
 * - 운영에서 룰 변경이 잦으므로 properties 기반으로 주입합니다.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.publish-policy")
public class GatewayPublishPolicyProperties {

    private String version = "default";

    /**
     * PublishPolicySpec의 updatedAtEpochMs(필수 필드)
     * - 운영에서 hot-reload 시점 비교에 사용
     * - properties에 값이 없으면 toSpec()에서 "현재 시각"으로 채운다.
     */
    private long updatedAtEpochMs = 0L;

    private PublishMode defaultMode = PublishMode.OUTBOX;

    private List<Rule> rules = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    public PublishMode getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(final PublishMode defaultMode) {
        this.defaultMode = defaultMode;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(final long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(final List<Rule> rules) {
        this.rules = rules;
    }

    public PublishPolicySpec toSpec() {
        final List<PublishPolicyRule> mappedRules = rules.stream()
                .map(rule -> new PublishPolicyRule(
                        rule.matchType,
                        rule.pattern,
                        rule.publishMode,
                        rule.topic,
                        rule.key,
                        rule.headers
                ))
                .toList();

        // updatedAtEpochMs는 spec의 필수 값이므로 0/미지정이면 현재 시각으로 채운다.
        // - config 파일에서 명시하면 그 값을 그대로 사용
        final long resolvedUpdatedAt = updatedAtEpochMs > 0
                ? updatedAtEpochMs
                : System.currentTimeMillis();

        return new PublishPolicySpec(
                version,
                resolvedUpdatedAt,
                defaultMode,
                mappedRules
        );
    }

    public static final class Rule {
        private MessageMatchType matchType = MessageMatchType.EXACT;
        private String pattern = "";
        private PublishMode publishMode = PublishMode.OUTBOX;
        private String topic;
        private String key;
        private Map<String, String> headers = Map.of();

        public MessageMatchType getMatchType() {
            return matchType;
        }

        public void setMatchType(final MessageMatchType matchType) {
            this.matchType = matchType;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(final String pattern) {
            this.pattern = pattern;
        }

        public PublishMode getPublishMode() {
            return publishMode;
        }

        public void setPublishMode(final PublishMode publishMode) {
            this.publishMode = publishMode;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(final String topic) {
            this.topic = topic;
        }

        public String getKey() {
            return key;
        }

        public void setKey(final String key) {
            this.key = key;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(final Map<String, String> headers) {
            this.headers = headers;
        }
    }
}
