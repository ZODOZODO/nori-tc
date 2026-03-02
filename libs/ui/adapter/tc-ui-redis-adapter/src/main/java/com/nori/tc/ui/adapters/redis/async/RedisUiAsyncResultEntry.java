package com.nori.tc.ui.adapters.redis.async;

import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;

import java.io.Serializable;

/**
 * Redis 저장용 비동기 작업 결과 엔트리입니다.
 *
 * <p>역할:</p>
 * <p>{@link KafkaUiTaskReplyMessage}는 record 타입으로 {@link Serializable}을 구현하지 않습니다.
 * Redis에 JDK 직렬화 방식으로 저장하기 위해 동일한 필드를 담는 어댑터 전용 래퍼 클래스입니다.</p>
 *
 * <p>키 형식: {@code tc:ui:backend:async:{traceId}}</p>
 * <p>TTL: {@code tc.ui.backend.async.result-ttl-seconds} 설정값을 따릅니다 (기본 600초).</p>
 *
 * <p>직렬화 호환성:</p>
 * <p>필드 추가·삭제 시 {@code serialVersionUID}를 갱신해야 합니다.</p>
 */
public class RedisUiAsyncResultEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    // --- metadata 필드 (KafkaUiTaskMessage.KafkaUiTaskMetadata에서 복사) ---

    /** 이벤트 타입 (예: EQP_START, EQP_END) */
    private String eventType;

    /** 메시지 발행 시각 (ISO-8601 문자열) */
    private String timestamp;

    /** 메시지 발행 출처 (예: TC-COMM-GATEWAY, TC-BUSINESS-CORE) */
    private String source;

    /** 작업 추적 ID */
    private String traceId;

    // --- data 필드 (KafkaUiTaskReplyMessage.KafkaUiTaskReplyData에서 복사) ---

    /** 설비 ID */
    private String eqpId;

    /** 인터페이스 타입 */
    private String interfaceType;

    /** 처리 결과 (PASS / FAIL) */
    private String status;

    /** 오류 메시지 (실패 시 설정) */
    private String errorMsg;

    /** 오류 코드 (실패 시 설정) */
    private String errorCode;

    /**
     * Redis 직렬화 프레임워크용 기본 생성자입니다.
     */
    protected RedisUiAsyncResultEntry() {
    }

    /**
     * {@link KafkaUiTaskReplyMessage}를 Redis 저장용 엔트리로 변환합니다.
     *
     * @param reply 변환할 Kafka reply 메시지
     * @return Redis 저장용 엔트리
     */
    public static RedisUiAsyncResultEntry from(final KafkaUiTaskReplyMessage reply) {
        final RedisUiAsyncResultEntry entry = new RedisUiAsyncResultEntry();
        entry.traceId = reply.metadata().traceId();
        entry.eventType = reply.metadata().eventType();
        entry.timestamp = reply.metadata().timestamp();
        entry.source = reply.metadata().source();
        entry.eqpId = reply.data().eqpId();
        entry.interfaceType = reply.data().interfaceType();
        entry.status = reply.data().STATUS();
        entry.errorMsg = reply.data().ERRORMSG();
        entry.errorCode = reply.data().ERRORCODE();
        return entry;
    }

    /**
     * Redis에서 읽은 엔트리를 도메인 계약 객체 {@link KafkaUiTaskReplyMessage}로 복원합니다.
     *
     * @return 복원된 KafkaUiTaskReplyMessage
     */
    public KafkaUiTaskReplyMessage toReplyMessage() {
        // KafkaUiTaskMetadata(eventType, timestamp, source, traceId) — 필드 순서 일치
        final KafkaUiTaskMessage.KafkaUiTaskMetadata metadata =
                new KafkaUiTaskMessage.KafkaUiTaskMetadata(eventType, timestamp, source, traceId);
        final KafkaUiTaskReplyMessage.KafkaUiTaskReplyData data =
                new KafkaUiTaskReplyMessage.KafkaUiTaskReplyData(
                        eqpId, interfaceType, status, errorMsg, errorCode);
        return new KafkaUiTaskReplyMessage(metadata, data);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSource() {
        return source;
    }

    public String getEqpId() {
        return eqpId;
    }

    public String getInterfaceType() {
        return interfaceType;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
