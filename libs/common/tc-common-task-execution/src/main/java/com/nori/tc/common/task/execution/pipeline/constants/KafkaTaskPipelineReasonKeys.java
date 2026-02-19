package com.nori.tc.common.task.execution.pipeline.constants;

/**
 * Kafka 태스크 파이프라인 단계별 실패 사유 키 상수입니다.
 *
 * <p>이 상수는 다음 목적에 공통으로 사용됩니다.</p>
 * <p>1) DLQ reasonCode 매핑</p>
 * <p>2) 처리 결과 로그(disposition) 분류</p>
 * <p>3) 메트릭/알람 집계 차원 통일</p>
 */
public final class KafkaTaskPipelineReasonKeys {

    /**
     * 라우팅 단계에서 실패한 경우 사용합니다.
     */
    public static final String ROUTING_FAILED = "ROUTING_FAILED";

    /**
     * 처리기 실행 단계에서 실패한 경우 사용합니다.
     */
    public static final String PROCESS_FAILED = "PROCESS_FAILED";

    /**
     * 응답 발행 단계에서 실패한 경우 사용합니다.
     */
    public static final String PUBLISH_FAILED = "PUBLISH_FAILED";

    /**
     * 유틸리티 클래스의 인스턴스 생성을 방지합니다.
     */
    private KafkaTaskPipelineReasonKeys() {
    }
}
