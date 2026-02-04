package com.nori.tc.db.mybatis.common.store;

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
import com.nori.tc.db.core.model.NewTcModel;
import com.nori.tc.db.core.model.TcModelSearchCriteria;
import com.nori.tc.db.core.model.TcModelStore;
import com.nori.tc.db.core.model.UpdateTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.mybatis.common.mapper.TcModelMapper;

/**
 * tc_model MyBatis Store 구현체.
 *
 * 생성(create) 주의
 * - common-schema의 TcModelMapper.xml은 벤더 중립성을 위해 "generated key 반환"을 하지 않는다.
 * - 따라서 insert 후 (model_name, model_version)으로 재조회하여 model_key를 확보한다.
 */
@Repository
public class TcModelMybatisStore implements TcModelStore {

    private final TcModelMapper mapper;

    public TcModelMybatisStore(TcModelMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModel create(NewTcModel command) {
        final String modelName = command.modelName();
        final String modelVersion = command.modelVersion();

        // model_key/created_at/updated_at은 DB가 생성한다.
        final TcModel row = new TcModel(
                0L,
                modelName,
                modelVersion,
                command.protocolType(),
                command.status(),
                null,
                null
        );

        try {
            int inserted = mapper.insert(row);
            if (inserted != 1) {
                throw new DbAccessException("tc_model insert affected rows != 1. affected=" + inserted);
            }

            // insert 후 유니크 키로 재조회하여 model_key 포함된 row 반환
            return mapper.findByNameVersion(modelName, modelVersion)
                    .orElseThrow(() -> new DbAccessException("tc_model insert succeeded but row not found. nameVersion=" + modelName + "/" + modelVersion));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model duplicate (model_name, model_version). nameVersion=" + modelName + "/" + modelVersion, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model create failed. nameVersion=" + modelName + "/" + modelVersion, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model create failed (unexpected). nameVersion=" + modelName + "/" + modelVersion, e);
        }
    }

    @Override
    @Transactional
    public TcModel update(UpdateTcModel command) {
        final long modelKey = command.modelKey();

        final TcModel row = new TcModel(
                modelKey,
                command.modelName(),
                command.modelVersion(),
                command.protocolType(),
                command.status(),
                null,
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                throw new DbEntityNotFoundException("tc_model not found for update. modelKey=" + modelKey);
            }

            return mapper.findByModelKey(modelKey)
                    .orElseThrow(() -> new DbAccessException("tc_model update succeeded but row not found. modelKey=" + modelKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model update duplicate (model_name, model_version). modelKey=" + modelKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model update failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model update failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByModelKey(long modelKey) {
        try {
            return mapper.findByModelKey(modelKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model findByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model findByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

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

    @Override
    @Transactional(readOnly = true)
    public List<TcModel> findAll(TcModelSearchCriteria criteria, PageRequest page) {
        // Null safe
        final TcModelSearchCriteria c = (criteria == null) ? TcModelSearchCriteria.empty() : criteria;
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            // FIX: DB 페이징 적용
            return mapper.findAll(
                    c.modelNameLike(),
                    c.protocolType(),
                    c.status(),
                    p.offset(),
                    p.limit()
            );
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model findAll failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model findAll failed (unexpected).", e);
        }
    }

    @Override
    @Transactional
    public void deleteByModelKey(long modelKey) {
        try {
            // 삭제는 멱등으로 둔다: 없어도 예외를 던지지 않는다.
            mapper.deleteByModelKey(modelKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model deleteByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model deleteByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }
}