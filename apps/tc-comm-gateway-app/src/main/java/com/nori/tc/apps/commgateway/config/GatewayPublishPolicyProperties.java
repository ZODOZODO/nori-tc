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

        return new PublishPolicySpec(
                version,
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
