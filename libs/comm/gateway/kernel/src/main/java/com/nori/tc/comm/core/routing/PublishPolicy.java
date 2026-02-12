package com.nori.tc.comm.core.routing;

import com.nori.tc.comm.core.message.ParsedMessage;

/**
 * 발행 정책 인터페이스
 *
 * 구현 예)
 * - PublishPolicyEngine: 선언형 룰(PublishPolicySpec)을 기반으로 빠르게 결정
 * - 향후 확장: 설비별/모델별 정책, 시간대별 정책 등
 */
public interface PublishPolicy {

    PublishDecision decide(ParsedMessage message);
}
