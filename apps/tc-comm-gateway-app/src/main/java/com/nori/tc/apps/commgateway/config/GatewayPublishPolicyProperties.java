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
 * Publish policy configuration for OUTBOX vs DIRECT_KAFKA.
 *
 * - Properties are mapped to a PublishPolicySpec used by the engine.
 * - updatedAtEpochMs is required by the spec for hot-reload comparisons.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.publish-policy")
public class GatewayPublishPolicyProperties {

    private String version = "default";

    /**
     * Timestamp used by PublishPolicySpec.
     * - If not set, toSpec() fills it with the current time.
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
