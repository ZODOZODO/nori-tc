package com.nori.tc.comm.core.routing;

import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.routing.spec.MessageMatchType;
import com.nori.tc.comm.core.routing.spec.PublishPolicyRule;
import com.nori.tc.comm.core.routing.spec.PublishPolicySpec;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 선언형 PublishPolicy 구현체
 *
 * 목적
 * - 메시지명 기준으로 OUTBOX vs DIRECT_KAFKA를 빠르게 결정합니다.
 * - 운영에서 룰이 자주 바뀌므로, spec을 원자적으로 교체(Atomic swap)하기 쉬운 불변 객체 형태로 둡니다.
 *
 * 룰 평가 순서
 * - rules를 "위에서 아래" 순서로 평가하며, 첫 매칭 룰이 적용됩니다.
 * - 어떤 룰도 매칭되지 않으면 defaultMode가 적용됩니다(권장: OUTBOX).
 *
 * 성능
 * - REGEX 룰은 미리 Pattern 컴파일하여 캐시에 담습니다.
 */
public final class PublishPolicyEngine implements PublishPolicy {

    private final PublishMode defaultMode;
    private final List<CompiledRule> compiledRules;
    private final String version; // 운영 태깅(policyVersion 등)

    
    /**
     * 통신 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param spec 통신 코어 모듈 처리에 사용하는 입력 값
     */
    public PublishPolicyEngine(final PublishPolicySpec spec) {
        // 출력 단계: 결과를 외부 저장소/브로커로 반영합니다.
        Objects.requireNonNull(spec, "spec is null");
        this.defaultMode = Objects.requireNonNull(spec.defaultMode(), "defaultMode is null");
        this.version = spec.version();

        this.compiledRules = spec.rules().stream()
                .map(CompiledRule::new)
                .toList();
    }

    
    /**
     * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @return 통신 코어 모듈 처리 결과
     */
    public String version() {
        return version;
    }

    
    /**
     * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param message 처리할 원본 데이터
     * @return 통신 코어 모듈 처리 결과
     */
    @Override
    public PublishDecision decide(final ParsedMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String msgName = message.messageName().value();

        for (CompiledRule rule : compiledRules) {
            if (rule.matches(msgName)) {
                return new PublishDecision(
                        rule.publishMode,
                        rule.topic,
                        rule.key,
                        rule.headers
                );
            }
        }

        // 아무 룰도 매칭되지 않으면 default 적용(권장: OUTBOX)
        return new PublishDecision(defaultMode, null, null, java.util.Map.of());
    }

    // -------------------------
    // Internal: compiled rule
    // -------------------------

    private static final class CompiledRule {
        private final MessageMatchType matchType;
        private final String patternText;
        private final Pattern regexPattern; // matchType=REGEX일 때만 사용

        private final PublishMode publishMode;
        private final String topic;
        private final String key;
        private final java.util.Map<String, String> headers;

        
        /**
         * CompiledRule 생성자를 초기화합니다.
         *
         * @param rule 입력 값
         */

        private CompiledRule(final PublishPolicyRule rule) {
            Objects.requireNonNull(rule, "rule is null");
            this.matchType = Objects.requireNonNull(rule.matchType(), "matchType is null");
            this.patternText = Objects.requireNonNull(rule.pattern(), "pattern is null");

            this.publishMode = Objects.requireNonNull(rule.publishMode(), "publishMode is null");
            this.topic = rule.topic();
            this.key = rule.key();
            this.headers = rule.headers() == null ? java.util.Map.of() : rule.headers();

            if (matchType == MessageMatchType.REGEX) {
                this.regexPattern = Pattern.compile(patternText);
            } else {
                this.regexPattern = null;
            }
        }

        
        /**
         * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
         *
         * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
         * @param messageName 처리할 원본 데이터
         * @return 처리 성공 여부
         */
        private boolean matches(final String messageName) {
            return switch (matchType) {
                case EXACT -> messageName.equals(patternText);
                case PREFIX -> messageName.startsWith(patternText);
                case CONTAINS -> messageName.contains(patternText);
                case REGEX -> regexPattern.matcher(messageName).matches();
            };
        }
    }
}
