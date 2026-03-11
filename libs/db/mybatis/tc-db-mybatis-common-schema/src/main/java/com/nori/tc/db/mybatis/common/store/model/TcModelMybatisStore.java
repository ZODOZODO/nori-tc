package com.nori.tc.db.mybatis.common.store.model;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelMapper;

@Repository
public class TcModelMybatisStore implements TcModelStore {

    private final TcModelMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelMybatisStore(TcModelMapper mapper) {
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
    public TcModel upsert(UpsertTcModel command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (command == null) throw new IllegalArgumentException("UpsertTcModel must not be null");

        TcModel row = toRow(command);

        try {
            if (row.modelKey() > 0) {
                mapper.update(row);
            } else {
                mapper.insert(row);
            }
            // model_version_key가 반환되지 않으므로 이름/버전으로 재조회한다.
            return mapper.findByNameVersion(command.modelName(), command.modelVersion())
                    .orElseThrow(() -> new DbAccessException("tc_model upsert failed: cannot re-fetch by name/version"));

        } catch (DataAccessException e) {
            throw new DbDuplicateKeyException("tc_model upsert failed. nameVersion=" + command.modelName() + "/" + command.modelVersion(), e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model upsert failed (unexpected).", e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByModelVersionKey(long modelVersionKey) {
        try {
            return mapper.findByModelVersionKey(modelVersionKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model findByModelVersionKey failed. modelVersionKey=" + modelVersionKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model findByModelVersionKey failed (unexpected). modelVersionKey=" + modelVersionKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelName 도메인 데이터 객체
     * @param modelVersion 도메인 데이터 객체
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByNameVersion(String modelName, String modelVersion) {
        try {
            return mapper.findByNameVersion(modelName, modelVersion);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model findByNameVersion failed. nameVersion=" + modelName + "/" + modelVersion, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model findByNameVersion failed (unexpected). nameVersion=" + modelName + "/" + modelVersion, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModel> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAll(p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model findAll failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model findAll failed (unexpected).", e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByModelVersionKey(long modelVersionKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            // 삭제는 멱등으로 둔다: 없어도 예외를 던지지 않는다.
            mapper.deleteByModelVersionKey(modelVersionKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model deleteByModelVersionKey failed. modelVersionKey=" + modelVersionKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model deleteByModelVersionKey failed (unexpected). modelVersionKey=" + modelVersionKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private TcModel toRow(UpsertTcModel command) {
        long modelVersionKey = command.modelKey() == null ? 0L : command.modelKey();
        return new TcModel(
                modelVersionKey,
                0L,
                command.modelName(),
                command.parentModel(),
                command.modelVersion(),
                command.commInterface(),
                command.status(),
                command.description(),
                command.maker(),
                null,
                null,
                command.createdBy(),
                command.updatedBy()
        );
    }
}
