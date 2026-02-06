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
 * - 따라서 insert 후 (model_key, mdf_name)으로 재조회하여 mdf_key를 확보한다.
 */
@Repository
public class TcModelMdfMybatisStore implements TcModelMdfStore {

    private final TcModelMdfMapper mapper;

    public TcModelMdfMybatisStore(TcModelMdfMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelMdf upsert(UpsertTcModelMdf command) {
        final Long mdfKey = command.mdfKey();
        final long modelKey = command.modelKey();
        final String mdfName = command.mdfName();

        final long resolvedKey = resolveKey(mdfKey, modelKey, mdfName);

        final TcModelMdf row = new TcModelMdf(
                resolvedKey,
                modelKey,
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

            return mapper.findByModelKeyAndName(modelKey, mdfName)
                    .orElseThrow(() -> new DbAccessException("tc_model_mdf upsert succeeded but row not found. modelKey/name=" + modelKey + "/" + mdfName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_mdf upsert duplicate (model_key, mdf_name). modelKey/name=" + modelKey + "/" + mdfName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf upsert failed. modelKey/name=" + modelKey + "/" + mdfName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf upsert failed (unexpected). modelKey/name=" + modelKey + "/" + mdfName, e);
        }
    }

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

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByModelKeyAndName(long modelKey, String mdfName) {
        try {
            return mapper.findByModelKeyAndName(modelKey, mdfName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf findByModelKeyAndName failed. modelKey/name=" + modelKey + "/" + mdfName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf findByModelKeyAndName failed (unexpected). modelKey/name=" + modelKey + "/" + mdfName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelMdf> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf findAllByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf findAllByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByMdfKey(long mdfKey) {
        try {
            mapper.deleteByMdfKey(mdfKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf deleteByMdfKey failed. mdfKey=" + mdfKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf deleteByMdfKey failed (unexpected). mdfKey=" + mdfKey, e);
        }
    }

    private long resolveKey(Long mdfKey, long modelKey, String mdfName) {
        if (mdfKey != null) {
            if (mdfKey <= 0) {
                throw new IllegalArgumentException("mdfKey must be > 0 when provided");
            }
            return mdfKey;
        }

        return mapper.findByModelKeyAndName(modelKey, mdfName)
                .map(TcModelMdf::mdfKey)
                .orElse(0L);
    }
}