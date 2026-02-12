package com.nori.tc.comm.core.port;

import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.routing.PublishDecision;

/**
 * Outbox 적재 Port (OUTBOX 경로)
 *
 * 목적
 * - core 엔진은 DB/JPA/MyBatis를 모릅니다.
 * - 앱이 DB 모듈을 사용하여 outbox row를 insert 합니다.
 */
public interface OutboxWriterPort {

    
    /**
     * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param message 처리할 원본 데이터
     * @param decision 통신 코어 모듈 처리에 사용하는 입력 값
     */
    void write(ParsedMessage message, PublishDecision decision) throws Exception;
}
