package com.nori.tc.apps.commgateway.config;

import com.nori.tc.comm.core.routing.PublishMode;
import com.nori.tc.comm.core.routing.spec.MessageMatchType;
import com.nori.tc.comm.core.routing.spec.PublishPolicyRule;
import com.nori.tc.comm.core.routing.spec.PublishPolicySpec;
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

    private String version;

    /**
     * Timestamp used by PublishPolicySpec.
     * - Must be provided via properties or DB-backed refresh.
     */
    private Long updatedAtEpochMs;

    private PublishMode defaultMode;

    private List<Rule> rules;

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
    }

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
