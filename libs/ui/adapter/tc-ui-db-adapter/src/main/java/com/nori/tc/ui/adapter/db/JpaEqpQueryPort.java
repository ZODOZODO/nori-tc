package com.nori.tc.ui.adapter.db;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.ui.core.exception.UiBadRequestException;
import com.nori.tc.ui.core.model.PagedResponse;
import com.nori.tc.ui.core.port.db.EqpQueryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link EqpQueryPort}의 DB Store 기반 구현체입니다.
 *
 * <p>역할:</p>
 * <ul>
 *   <li>{@code GET /api/eqp} 목록 조회</li>
 *   <li>{@code GET /api/eqp/{eqpId}} 단건 조회</li>
 * </ul>
 *
 * <p>구현 원칙:</p>
 * <ul>
 *   <li>조회 SSOT는 {@code tc_eqp}이며, {@link TcEqpStore}만 사용합니다.</li>
 *   <li>입력값 검증 실패는 {@link UiBadRequestException}(400 대응)로 변환합니다.</li>
 * </ul>
 */
@Repository
public class JpaEqpQueryPort implements EqpQueryPort {

    private static final Logger log = LoggerFactory.getLogger(JpaEqpQueryPort.class);

    private final TcEqpStore eqpStore;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param eqpStore tc_eqp Store 포트
     */
    public JpaEqpQueryPort(final TcEqpStore eqpStore) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        log.info("JpaEqpQueryPort initialized. source=tc_eqp");
    }

    /**
     * 설비 목록을 페이지 단위로 조회합니다.
     *
     * <p>count 계산은 다음 규칙을 따릅니다.</p>
     * <ol>
     *   <li>현재 페이지가 마지막이면(offset + pageSize)로 즉시 계산</li>
     *   <li>마지막 여부를 확정할 수 없으면 별도 count 스캔 수행</li>
     * </ol>
     *
     * @param pageRequest offset/limit 페이지 요청(없으면 기본 페이지 사용)
     * @return 목록 + count를 포함한 페이지 응답
     */
    @Override
    public PagedResponse<TcEqp> findAll(final PageRequest pageRequest) {
        final PageRequest effectivePage = (pageRequest == null) ? PageRequest.defaultPage() : pageRequest;

        if (log.isDebugEnabled()) {
            log.debug("설비 목록 조회 시작. offset={}, limit={}",
                    effectivePage.offset(), effectivePage.limit());
        }

        try {
            final List<TcEqp> items = eqpStore.findAll(effectivePage);
            final long totalCount = UiDbPagedCountSupport.resolveTotalCount(
                    items,
                    effectivePage,
                    UiDbPagedCountSupport.DEFAULT_COUNT_SCAN_LIMIT,
                    eqpStore::findAll,
                    log,
                    "eqp"
            );

            if (log.isDebugEnabled()) {
                log.debug("설비 목록 조회 완료. offset={}, limit={}, pageSize={}, totalCount={}",
                        effectivePage.offset(), effectivePage.limit(), items.size(), totalCount);
            }

            return PagedResponse.of(items, effectivePage.offset(), effectivePage.limit(), totalCount);
        } catch (RuntimeException e) {
            if (UiDbAdapterExceptionSupport.isBadRequest(e)) {
                log.warn("설비 목록 조회 요청 거부 - 잘못된 입력. offset={}, limit={}, reason={}",
                        effectivePage.offset(), effectivePage.limit(),
                        UiDbAdapterExceptionSupport.resolveMessage(e, "invalid page request"));
                throw new UiBadRequestException("설비 목록 조회 입력이 올바르지 않습니다.", e);
            }
            log.error("설비 목록 조회 실패. offset={}, limit={}",
                    effectivePage.offset(), effectivePage.limit(), e);
            throw e;
        }
    }

    /**
     * 설비 ID(eqpId)로 단건을 조회합니다.
     *
     * @param eqpId 설비 비즈니스 키
     * @return 조회 결과(없으면 빈 Optional)
     */
    @Override
    public Optional<TcEqp> findByEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            log.warn("설비 단건 조회 요청 거부 - eqpId가 비어 있습니다.");
            throw new UiBadRequestException("eqpId는 비어 있을 수 없습니다.");
        }

        if (log.isDebugEnabled()) {
            log.debug("설비 단건 조회 시작. eqpId={}", eqpId);
        }

        try {
            final Optional<TcEqp> result = eqpStore.findByEqpId(eqpId);
            if (log.isDebugEnabled()) {
                log.debug("설비 단건 조회 완료. eqpId={}, found={}", eqpId, result.isPresent());
            }
            return result;
        } catch (RuntimeException e) {
            if (UiDbAdapterExceptionSupport.isBadRequest(e)) {
                log.warn("설비 단건 조회 요청 거부 - 잘못된 입력. eqpId={}, reason={}",
                        eqpId, UiDbAdapterExceptionSupport.resolveMessage(e, "invalid eqpId"));
                throw new UiBadRequestException("설비 조회 입력이 올바르지 않습니다.", e);
            }
            log.error("설비 단건 조회 실패. eqpId={}", eqpId, e);
            throw e;
        }
    }
}
