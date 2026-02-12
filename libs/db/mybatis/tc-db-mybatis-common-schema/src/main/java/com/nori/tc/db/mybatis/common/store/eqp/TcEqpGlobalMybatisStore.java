package com.nori.tc.db.mybatis.common.store.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpGlobalStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpGlobal;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpGlobal;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpGlobalMapper;

/**
 * tc_eqp_global MyBatis Store 구현체.
 *
 * - 유니크 키(eqp_key, param_name) 기반 upsert 제공
 */
@Repository
public class TcEqpGlobalMybatisStore implements TcEqpGlobalStore {

    private final TcEqpGlobalMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcEqpGlobalMybatisStore(TcEqpGlobalMapper mapper) {
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
    public TcEqpGlobal upsert(UpsertTcEqpGlobal command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        final long eqpKey = command.eqpKey();
        final String paramName = command.paramName();

        final TcEqpGlobal row = new TcEqpGlobal(
                0L,
                eqpKey,
                paramName,
                command.paramValue(),
                command.updatedAt()
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

            return mapper.findByEqpKeyAndParamName(eqpKey, paramName)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_global upsert succeeded but row not found. eqpKey=" + eqpKey + ", paramName=" + paramName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_global upsert duplicate key. eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_global upsert failed. eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_global upsert failed (unexpected). eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpGlobal> findByEqpKeyAndParamName(long eqpKey, String paramName) {
        try {
            return mapper.findByEqpKeyAndParamName(eqpKey, paramName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_global findByEqpKeyAndParamName failed. eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_global findByEqpKeyAndParamName failed (unexpected). eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcEqpGlobal> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_global findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_global findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByEqpKeyAndParamName(long eqpKey, String paramName) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByEqpKeyAndParamName(eqpKey, paramName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_global deleteByEqpKeyAndParamName failed. eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_global deleteByEqpKeyAndParamName failed (unexpected). eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpGlobal command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
        if (command.paramName() == null || command.paramName().isBlank()) throw new IllegalArgumentException("command.paramName must not be null/blank");
    }
}
