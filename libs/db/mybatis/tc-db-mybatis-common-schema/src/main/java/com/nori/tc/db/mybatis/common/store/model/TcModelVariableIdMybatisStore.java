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
import com.nori.tc.db.core.model.store.TcModelVariableIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelVariableId;
import com.nori.tc.db.domain.common.model.VariableIdType;
import com.nori.tc.db.domain.model.TcModelVariableId;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelVariableIdMapper;

/**
 * tc_model_variableid MyBatis Store 구현체.
 */
@Repository
public class TcModelVariableIdMybatisStore implements TcModelVariableIdStore {

    private final TcModelVariableIdMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelVariableIdMybatisStore(TcModelVariableIdMapper mapper) {
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
    public TcModelVariableId upsert(UpsertTcModelVariableId command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final long modelKey = command.modelKey();
        final VariableIdType variableIdType = command.variableIdType();
        final String variableId = command.variableId();

        final TcModelVariableId row = new TcModelVariableId(
                0L,
                modelKey,
                variableId,
                variableIdType,
                command.description(),
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

            return mapper.findByModelKeyAndTypeAndVariableId(modelKey, variableIdType, variableId)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model_variableid upsert succeeded but row not found. modelKey=" + modelKey
                                    + ", variableIdType=" + variableIdType + ", variableId=" + variableId
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_model_variableid upsert duplicate key. modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_variableid upsert failed. modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_variableid upsert failed (unexpected). modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param variableKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelVariableId> findByVariableKey(long variableKey) {
        try {
            return mapper.findByVariableKey(variableKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_variableid findByVariableKey failed. variableKey=" + variableKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_variableid findByVariableKey failed (unexpected). variableKey=" + variableKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param variableIdType DB MyBatis 계층 처리에 사용하는 입력 값
     * @param variableId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelVariableId> findByModelKeyAndTypeAndVariableId(
            long modelKey,
            VariableIdType variableIdType,
            String variableId
    ) {
        try {
            return mapper.findByModelKeyAndTypeAndVariableId(modelKey, variableIdType, variableId);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_variableid findByModelKeyAndTypeAndVariableId failed. modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_variableid findByModelKeyAndTypeAndVariableId failed (unexpected). modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
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
    public List<TcModelVariableId> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_variableid findAllByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_variableid findAllByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param variableKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByVariableKey(long variableKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByVariableKey(variableKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_variableid deleteByVariableKey failed. variableKey=" + variableKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_variableid deleteByVariableKey failed (unexpected). variableKey=" + variableKey, e);
        }
    }
}
