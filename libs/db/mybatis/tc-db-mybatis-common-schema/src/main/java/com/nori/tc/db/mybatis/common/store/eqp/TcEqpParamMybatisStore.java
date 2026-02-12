package com.nori.tc.db.mybatis.common.store.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpParamStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParam;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpParamMapper;

/**
 * tc_eqp_param MyBatis Store 구현체.
 */
@Repository
public class TcEqpParamMybatisStore implements TcEqpParamStore {

    private final TcEqpParamMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcEqpParamMybatisStore(TcEqpParamMapper mapper) {
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
    public TcEqpParam upsert(UpsertTcEqpParam command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final long eqpKey = command.eqpKey();
        final String paramName = command.paramName();
        final String paramVersion = command.paramVersion();

        final TcEqpParam row = new TcEqpParam(
                0L,
                eqpKey,
                paramName,
                paramVersion,
                command.paramValue(),
                null
        );

        try {
            int updated = mapper.updateByUniqueKey(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.updateByUniqueKey(row);
                }
            }

            return mapper.findByEqpKeyAndNameVersion(eqpKey, paramName, paramVersion)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_eqp_param upsert succeeded but row not found. eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_eqp_param upsert duplicate key. eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_eqp_param upsert failed. eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_eqp_param upsert failed (unexpected). eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB MyBatis 계층 처리에 사용하는 입력 값
     * @param paramVersion DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpParam> findByEqpKeyAndNameVersion(long eqpKey, String paramName, String paramVersion) {
        try {
            return mapper.findByEqpKeyAndNameVersion(eqpKey, paramName, paramVersion);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_eqp_param findByEqpKeyAndNameVersion failed. eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_eqp_param findByEqpKeyAndNameVersion failed (unexpected). eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
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
    public List<TcEqpParam> findAllByEqpKey(long eqpKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByEqpKey(eqpKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_param findAllByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_param findAllByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpParamKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByEqpParamKey(long eqpParamKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByEqpParamKey(eqpParamKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_param deleteByEqpParamKey failed. eqpParamKey=" + eqpParamKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_param deleteByEqpParamKey failed (unexpected). eqpParamKey=" + eqpParamKey, e);
        }
    }
}
