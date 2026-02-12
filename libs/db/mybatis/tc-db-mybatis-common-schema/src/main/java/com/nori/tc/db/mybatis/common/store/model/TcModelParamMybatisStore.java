package com.nori.tc.db.mybatis.common.store.model;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelParamStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelParam;
import com.nori.tc.db.domain.model.TcModelParam;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelParamMapper;

/**
 * tc_model_param MyBatis Store 구현체.
 */
@Repository
public class TcModelParamMybatisStore implements TcModelParamStore {

    private final TcModelParamMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelParamMybatisStore(TcModelParamMapper mapper) {
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
    public TcModelParam upsert(UpsertTcModelParam command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final long modelKey = command.modelKey();
        final String paramName = command.paramName();

        final TcModelParam row = new TcModelParam(
                0L,
                modelKey,
                paramName,
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

            return mapper.findByModelKeyAndName(modelKey, paramName)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model_param upsert succeeded but row not found. modelKey=" + modelKey + ", param=" + paramName
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_model_param upsert duplicate key. modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_param upsert failed. modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_param upsert failed (unexpected). modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param paramName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelParam> findByModelKeyAndName(long modelKey, String paramName) {
        try {
            return mapper.findByModelKeyAndName(modelKey, paramName);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_param findByModelKeyAndName failed. modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_param findByModelKeyAndName failed (unexpected). modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelParam> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_param findAllByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_param findAllByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelParamKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByModelParamKey(long modelParamKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByModelParamKey(modelParamKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_param deleteByModelParamKey failed. modelParamKey=" + modelParamKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_param deleteByModelParamKey failed (unexpected). modelParamKey=" + modelParamKey, e);
        }
    }
}
