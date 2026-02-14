package com.nori.tc.business.core.runtime;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.common.mailbox.MailboxTask;

/**
 * Mailbox 스케줄러에 적재되는 Business Core 작업 모델입니다.
 *
 * @param record 원본 입력 레코드
 * @param attempt 시도 횟수(1부터 시작)
 */
public record BusinessMailboxTask(
        BusinessInboundRecord record,
        int attempt
) implements MailboxTask {

    /**
     * 작업 생성 시 유효성 검증을 수행합니다.
     */
    public BusinessMailboxTask {
        if (record == null) {
            throw new IllegalArgumentException("record is required");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
    }

    /**
     * 공통 mailbox 라우팅 키(eqpId)를 반환합니다.
     *
     * @return eqpId
     */
    @Override
    public String routingKey() {
        return record.eqpId();
    }

    /**
     * 현재 작업의 다음 재시도 작업을 생성합니다.
     *
     * @return attempt가 1 증가된 새 작업
     */
    public BusinessMailboxTask nextAttempt() {
        return new BusinessMailboxTask(record, attempt + 1);
    }
}


