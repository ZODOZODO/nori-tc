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

    
    /**
     * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param message 처리할 원본 데이터
     * @return 통신 코어 모듈 처리 결과
     */
    PublishDecision decide(ParsedMessage message);
}
