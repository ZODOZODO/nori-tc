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

    void write(ParsedMessage message, PublishDecision decision) throws Exception;
}
