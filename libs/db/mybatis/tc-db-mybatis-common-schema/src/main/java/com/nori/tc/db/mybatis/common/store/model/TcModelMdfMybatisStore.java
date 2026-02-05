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
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.model.NewTcModelMdf;
import com.nori.tc.db.core.model.TcModelMdfStore;
import com.nori.tc.db.core.model.UpdateTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelMdfMapper;

/**
 * tc_model_mdf MyBatis Store 구현체.
 *
 * 생성(create) 주의
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
    public TcModelMdf create(NewTcModelMdf command) {
        final long modelKey = command.modelKey();
        final String mdfName = command.mdfName();

        // mdf_key/updated_at은 DB가 생성한다.
        final TcModelMdf row = new TcModelMdf(
                0L,
                modelKey,
                mdfName,
                command.mdfFile(),
                null
        );

        try {
            int inserted = mapper.insert(row);
            if (inserted != 1) {
                throw new DbAccessException("tc_model_mdf insert affected rows != 1. affected=" + inserted);
            }

            // insert 후 유니크 키로 재조회하여 mdf_key 포함된 row 반환
            return mapper.findByModelKeyAndName(modelKey, mdfName)
                    .orElseThrow(() -> new DbAccessException("tc_model_mdf insert succeeded but row not found. modelKey/name=" + modelKey + "/" + mdfName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_mdf duplicate (model_key, mdf_name). modelKey/name=" + modelKey + "/" + mdfName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf create failed. modelKey/name=" + modelKey + "/" + mdfName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf create failed (unexpected). modelKey/name=" + modelKey + "/" + mdfName, e);
        }
    }

    @Override
    @Transactional
    public TcModelMdf update(UpdateTcModelMdf command) {
        final long mdfKey = command.mdfKey();

        final TcModelMdf row = new TcModelMdf(
                mdfKey,
                command.modelKey(),
                command.mdfName(),
                command.mdfFile(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                throw new DbEntityNotFoundException("tc_model_mdf not found for update. mdfKey=" + mdfKey);
            }

            return mapper.findByMdfKey(mdfKey)
                    .orElseThrow(() -> new DbAccessException("tc_model_mdf update succeeded but row not found. mdfKey=" + mdfKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_mdf update duplicate (model_key, mdf_name). mdfKey=" + mdfKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mdf update failed. mdfKey=" + mdfKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mdf update failed (unexpected). mdfKey=" + mdfKey, e);
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
}
