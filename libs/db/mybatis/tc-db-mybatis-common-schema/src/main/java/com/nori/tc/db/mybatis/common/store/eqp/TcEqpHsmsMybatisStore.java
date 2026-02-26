package com.nori.tc.db.mybatis.common.store.eqp;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpHsms;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpHsmsMapper;

/**
 * tc_eqp_hsms MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_key)
 * - created_at/updated_at은 DB/SQL이 관리(CURRENT_TIMESTAMP)하므로
 *   command의 createdAt/updatedAt은 "입력 DTO"로만 보관되고 실제 SQL에서는 반영되지 않을 수 있다.
 */
@Repository
public class TcEqpHsmsMybatisStore implements TcEqpHsmsStore {

    private final TcEqpHsmsMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcEqpHsmsMybatisStore(TcEqpHsmsMapper mapper) {
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
    public TcEqpHsms upsert(UpsertTcEqpHsms command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final long eqpKey = command.eqpKey();

        final TcEqpHsms row = new TcEqpHsms(
                eqpKey,
                command.deviceId(),
                command.t3Timeout(),
                command.t5Timeout(),
                command.t6Timeout(),
                command.t7Timeout(),
                command.t8Timeout(),
                command.linkTestEnabled(),
                command.linkTestInterval(),
                command.maxMsgBytes(),
                command.createdAt(),
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

            return mapper.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_hsms upsert succeeded but row not found. eqpKey=" + eqpKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_hsms upsert duplicate key. eqpKey=" + eqpKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms upsert failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms upsert failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpHsms> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     */
    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms deleteByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms deleteByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }
}
