package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;

/**
 * EQP_UPDATE_JARFILE 확장 처리 포인트입니다.
 *
 * <p>실제 jar 교체/검증/배포 로직은 운영 환경마다 달라질 수 있으므로
 * 인터페이스로 분리해 교체 가능한 구조로 유지합니다.</p>
 */
@FunctionalInterface
public interface GatewayUiJarfileTaskProcessor {

    /**
     * jarfile 업데이트 태스크를 처리합니다.
     *
     * @param message UI에서 수신한 원본 task 메시지
     * @param equipmentInfo DB/캐시에서 조회한 장비 런타임 프로필
     * @return UI 응답 발행에 사용할 처리 결과
     */
    GatewayUiTaskResult process(KafkaUiTaskMessage message, GatewayEquipmentInfo equipmentInfo);
}
