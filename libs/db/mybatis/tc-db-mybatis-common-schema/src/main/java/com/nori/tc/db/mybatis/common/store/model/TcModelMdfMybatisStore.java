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
 * {@code tc_model_mdf} MyBatis 저장소 구현입니다.
 *
 * <p>
 * 스키마 제약(UNIQUE: model_version_key)에 맞춰 upsert/조회 기준을
 * {@code modelVersionKey}로 통일합니다.
 * </p>
 */
@Repository
public class TcModelMdfMybatisStore implements TcModelMdfStore {

    private final TcModelMdfMapper mapper;

    /**
     * Mapper를 주입받습니다.
     */
    public TcModelMdfMybatisStore(TcModelMdfMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * MDF를 upsert 합니다.
     */
    @Override
    @Transactional
    public TcModelMdf upsert(UpsertTcModelMdf command) {
        validateUpsert(command);

        final long modelVersionKey = command.modelVersionKey();
        final long resolvedKey = resolveKey(command.mdfKey(), modelVersionKey);

        final TcModelMdf row = new TcModelMdf(
                resolvedKey,
                modelVersionKey,
                command.mdfName(),
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

            return mapper.findByModelVersionKey(modelVersionKey)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model_mdf upsert succeeded but row not found. modelVersionKey=" + modelVersionKey
                    ));
        } catch (DuplicateKeyException ex) {
            throw new DbDuplicateKeyException(
                    "tc_model_mdf upsert duplicate(model_version_key). modelVersionKey=" + modelVersionKey,
                    ex
            );
        } catch (DataAccessException ex) {
            throw new DbAccessException("tc_model_mdf upsert failed. modelVersionKey=" + modelVersionKey, ex);
        } catch (RuntimeException ex) {
            throw new DbAccessException("tc_model_mdf upsert failed (unexpected). modelVersionKey=" + modelVersionKey, ex);
        }
    }

    /**
     * {@code mdfKey} 기준으로 MDF를 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByMdfKey(long mdfKey) {
        if (mdfKey <= 0L) {
            throw new IllegalArgumentException("mdfKey must be > 0");
        }
        try {
            return mapper.findByMdfKey(mdfKey);
        } catch (DataAccessException ex) {
            throw new DbAccessException("tc_model_mdf findByMdfKey failed. mdfKey=" + mdfKey, ex);
        } catch (RuntimeException ex) {
            throw new DbAccessException("tc_model_mdf findByMdfKey failed (unexpected). mdfKey=" + mdfKey, ex);
        }
    }

    /**
     * {@code modelVersionKey} 기준으로 MDF 단건을 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByModelVersionKey(long modelVersionKey) {
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        try {
            return mapper.findByModelVersionKey(modelVersionKey);
        } catch (DataAccessException ex) {
            throw new DbAccessException("tc_model_mdf findByModelVersionKey failed. modelVersionKey=" + modelVersionKey, ex);
        } catch (RuntimeException ex) {
            throw new DbAccessException("tc_model_mdf findByModelVersionKey failed (unexpected). modelVersionKey=" + modelVersionKey, ex);
        }
    }

    /**
     * {@code modelVersionKey} 기준으로 MDF 목록을 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelMdf> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        final PageRequest normalizedPage = page == null ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelVersionKey(modelVersionKey, normalizedPage.offset(), normalizedPage.limit());
        } catch (DataAccessException ex) {
            throw new DbAccessException("tc_model_mdf findAllByModelVersionKey failed. modelVersionKey=" + modelVersionKey, ex);
        } catch (RuntimeException ex) {
            throw new DbAccessException("tc_model_mdf findAllByModelVersionKey failed (unexpected). modelVersionKey=" + modelVersionKey, ex);
        }
    }

    /**
     * {@code mdfKey} 기준으로 MDF를 삭제합니다.
     */
    @Override
    @Transactional
    public void deleteByMdfKey(long mdfKey) {
        if (mdfKey <= 0L) {
            throw new IllegalArgumentException("mdfKey must be > 0");
        }
        try {
            mapper.deleteByMdfKey(mdfKey);
        } catch (DataAccessException ex) {
            throw new DbAccessException("tc_model_mdf deleteByMdfKey failed. mdfKey=" + mdfKey, ex);
        } catch (RuntimeException ex) {
            throw new DbAccessException("tc_model_mdf deleteByMdfKey failed (unexpected). mdfKey=" + mdfKey, ex);
        }
    }

    /**
     * upsert 입력값을 검증합니다.
     */
    private void validateUpsert(UpsertTcModelMdf command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.mdfKey() != null && command.mdfKey() <= 0L) {
            throw new IllegalArgumentException("command.mdfKey must be > 0 when provided");
        }
        if (command.modelVersionKey() <= 0L) {
            throw new IllegalArgumentException("command.modelVersionKey must be > 0");
        }
        if (command.mdfName() == null || command.mdfName().isBlank()) {
            throw new IllegalArgumentException("command.mdfName must not be null/blank");
        }
        if (command.mdfFile() == null || command.mdfFile().length == 0) {
            throw new IllegalArgumentException("command.mdfFile must not be null/empty");
        }
    }

    /**
     * upsert 대상 키를 결정합니다.
     *
     * <p>
     * 1) mdfKey가 주어지면 해당 키를 사용합니다.
     * 2) 없으면 modelVersionKey 기준 기존 행을 조회해 키를 재사용합니다.
     * 3) 기존 행이 없으면 0을 반환해 update 실패 후 insert 경로를 타게 합니다.
     * </p>
     */
    private long resolveKey(Long mdfKey, long modelVersionKey) {
        if (mdfKey != null) {
            return mdfKey;
        }

        return mapper.findByModelVersionKey(modelVersionKey)
                .map(TcModelMdf::mdfKey)
                .orElse(0L);
    }
}
