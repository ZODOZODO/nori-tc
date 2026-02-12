package com.nori.tc.db.mybatis.common.store.work;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.work.store.TcWorkCarrierStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkCarrier;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkCarrierMapper;

/**
 * tc_work_carrier MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (work_key, carrier_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcWorkCarrierMybatisStore implements TcWorkCarrierStore {

    private final TcWorkCarrierMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcWorkCarrierMybatisStore(TcWorkCarrierMapper mapper) {
        this.mapper = mapper;
    }

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    @Transactional
    public TcWorkCarrier upsert(UpsertTcWorkCarrier command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        final long workKey = command.workKey();
        final String carrierId = command.carrierId();

        final TcWorkCarrier row = new TcWorkCarrier(
                0L,
                workKey,
                carrierId,
                command.portId(),
                command.slotMap(),
                command.totalQty(),
                command.goodQty(),
                command.scrapQty(),
                command.updatedAt() // SQL에서는 CURRENT_TIMESTAMP로 갱신(입력값은 참고용)
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.update(row);
                }
            }

            return mapper.findByWorkKeyCarrierId(workKey, carrierId)
                    .orElseThrow(() -> new DbAccessException("tc_work_carrier upsert succeeded but row not found. workKey/carrierId=" + workKey + "/" + carrierId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_work_carrier upsert duplicate key. workKey/carrierId=" + workKey + "/" + carrierId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier upsert failed. workKey/carrierId=" + workKey + "/" + carrierId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier upsert failed (unexpected). workKey/carrierId=" + workKey + "/" + carrierId, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param carrierId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkCarrier> findByWorkKeyCarrierId(long workKey, String carrierId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (carrierId == null || carrierId.isBlank()) {
            throw new IllegalArgumentException("carrierId must not be null/blank");
        }
        try {
            return mapper.findByWorkKeyCarrierId(workKey, carrierId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier findByWorkKeyCarrierId failed. workKey/carrierId=" + workKey + "/" + carrierId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier findByWorkKeyCarrierId failed (unexpected). workKey/carrierId=" + workKey + "/" + carrierId, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcWorkCarrier> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByWorkKey(workKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier findAllByWorkKey failed. workKey=" + workKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier findAllByWorkKey failed (unexpected). workKey=" + workKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param carrierId DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByWorkKeyCarrierId(long workKey, String carrierId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (carrierId == null || carrierId.isBlank()) {
            throw new IllegalArgumentException("carrierId must not be null/blank");
        }
        try {
            mapper.deleteByWorkKeyCarrierId(workKey, carrierId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier deleteByWorkKeyCarrierId failed. workKey/carrierId=" + workKey + "/" + carrierId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier deleteByWorkKeyCarrierId failed (unexpected). workKey/carrierId=" + workKey + "/" + carrierId, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcWorkCarrier command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workKey() <= 0) throw new IllegalArgumentException("command.workKey must be > 0");
        if (command.carrierId() == null || command.carrierId().isBlank()) throw new IllegalArgumentException("command.carrierId must not be null/blank");
    }
}
