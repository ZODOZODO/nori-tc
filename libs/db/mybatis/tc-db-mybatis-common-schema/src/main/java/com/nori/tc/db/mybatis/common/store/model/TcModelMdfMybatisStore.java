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
import com.nori.tc.db.core.model.store.TcModelMdfStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelMdfMapper;

/**
 * tc_model_mdf MyBatis Store 구현체.
 *
 * upsert 주의
 * - common-schema의 TcModelMdfMapper.xml은 벤더 중립성을 위해 "generated key 반환"을 하지 않는다.
 * - 따라서 insert 후 (model_version_key, mdf_name)으로 재조회하여 mdf_key를 확보한다.
 */
@Repository
public class TcModelMdfMybatisStore implements TcModelMdfStore {

    private final TcModelMdfMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelMdfMybatisStore(TcModelMdfMapper mapper) {
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
    public TcModelMdf upsert(UpsertTcModelMdf command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final Long mdfKey = command.mdfKey();
        final long modelVersionKey = command.modelVersionKey();
        final String mdfName = command.mdfName();

        final long resolvedKey = resolveKey(mdfKey, modelVersionKey, mdfName);

        final TcModelMdf row = new TcModelMdf(
                resolvedKey,
                modelVersionKey,
                mdfName,
                command.mdfFile(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_model_mdf insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByModelVersionKeyAndName(modelVersionKey, mdfName)
                    .orElseThrow(() -> new DbAccessException("tc_model_mdf upsert succeeded but row not found. modelVersionKey/name=" + modelVersionKey + "/" + mdfName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_mdf upsert duplicate (model_version_key, mdf_name). modelVersionKey/name=" + modelVersionKey + "/" + mdfName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf upsert failed. modelVersionKey/name=" + modelVersionKey + "/" + mdfName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf upsert failed (unexpected). modelVersionKey/name=" + modelVersionKey + "/" + mdfName, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByMdfKey(long mdfKey) {
        try {
            return mapper.findByMdfKey(mdfKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf findByMdfKey failed. mdfKey=" + mdfKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf findByMdfKey failed (unexpected). mdfKey=" + mdfKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param mdfName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByModelVersionKeyAndName(long modelVersionKey, String mdfName) {
        try {
            return mapper.findByModelVersionKeyAndName(modelVersionKey, mdfName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf findByModelVersionKeyAndName failed. modelVersionKey/name=" + modelVersionKey + "/" + mdfName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf findByModelVersionKeyAndName failed (unexpected). modelVersionKey/name=" + modelVersionKey + "/" + mdfName, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelMdf> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelVersionKey(modelVersionKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf findAllByModelVersionKey failed. modelVersionKey=" + modelVersionKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf findAllByModelVersionKey failed (unexpected). modelVersionKey=" + modelVersionKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByMdfKey(long mdfKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByMdfKey(mdfKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf deleteByMdfKey failed. mdfKey=" + mdfKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf deleteByMdfKey failed (unexpected). mdfKey=" + mdfKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     * @param modelVersionKey 대상 키 값
     * @param mdfName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    private long resolveKey(Long mdfKey, long modelVersionKey, String mdfName) {
        if (mdfKey != null) {
            if (mdfKey <= 0) {
                throw new IllegalArgumentException("mdfKey must be > 0 when provided");
            }
            return mdfKey;
        }

        return mapper.findByModelVersionKeyAndName(modelVersionKey, mdfName)
                .map(TcModelMdf::mdfKey)
                .orElse(0L);
    }
}