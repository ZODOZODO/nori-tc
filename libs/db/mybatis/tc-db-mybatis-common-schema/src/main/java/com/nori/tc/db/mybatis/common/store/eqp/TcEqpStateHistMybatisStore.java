package com.nori.tc.db.mybatis.common.store.eqp;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStateHistStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpStateHist;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpStateHist;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpStateHistMapper;

/**
 * tc_eqp_state_hist MyBatis Store 구현체.
 *
 * - 이력 테이블이므로 append(insert)만 제공
 */
@Repository
public class TcEqpStateHistMybatisStore implements TcEqpStateHistStore {

    private final TcEqpStateHistMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcEqpStateHistMybatisStore(TcEqpStateHistMapper mapper) {
        this.mapper = mapper;
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    @Override
    @Transactional
    public void append(UpsertTcEqpStateHist command) {
        UpsertTcEqpStateHist normalized = normalizeCommand(command);
        validateCommand(normalized);

        final TcEqpStateHist row = new TcEqpStateHist(
                0L,
                normalized.eqpKey(),
                normalized.stateType(),
                normalized.fromState(),
                normalized.toState(),
                normalized.changedAt(),
                normalized.reasonCode(),
                normalized.reasonDetail()
        );

        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_state_hist append duplicate key. eqpKey=" + normalized.eqpKey(), e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_state_hist append failed. eqpKey=" + normalized.eqpKey(), e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_state_hist append failed (unexpected). eqpKey=" + normalized.eqpKey(), e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcEqpStateHist> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByEqpKey(eqpKey, p.limit(), p.offset());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_state_hist findAllByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_state_hist findAllByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpStateHist command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.eqpKey() == null || command.eqpKey() <= 0) {
            throw new IllegalArgumentException("command.eqpKey must be positive");
        }
        if (command.stateType() == null) {
            throw new IllegalArgumentException("command.stateType must not be null");
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private UpsertTcEqpStateHist normalizeCommand(UpsertTcEqpStateHist command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcEqpStateHist(
                command.eqpKey(),
                command.stateType(),
                command.fromState(),
                command.toState(),
                command.changedAt() == null ? OffsetDateTime.now() : command.changedAt(),
                command.reasonCode(),
                command.reasonDetail()
        );
    }
}
