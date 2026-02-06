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

    public TcModelMybatisStore(TcModelMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModel upsert(UpsertTcModel command) {
        if (command == null) throw new IllegalArgumentException("UpsertTcModel must not be null");

        TcModel row = toRow(command);

        try {
            if (row.modelKey() > 0) {
                mapper.update(row);
            } else {
                mapper.insert(row);
            }
            // model_key가 반환되지 않으므로 이름/버전으로 재조회한다.
            return mapper.findByNameVersion(command.modelName(), command.modelVersion())
                    .orElseThrow(() -> new DbAccessException("tc_model upsert failed: cannot re-fetch by name/version"));

        } catch (DataAccessException e) {
            throw new DbDuplicateKeyException("tc_model upsert failed. nameVersion=" + command.modelName() + "/" + command.modelVersion(), e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model upsert failed (unexpected).", e);
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

    private TcModel toRow(UpsertTcModel command) {
        long modelKey = command.modelKey() == null ? 0L : command.modelKey();
        return new TcModel(
                modelKey,
                command.modelName(),
                command.modelVersion(),
                command.commInterface(),
                command.status(),
                command.maker(),
                null,
                null,
                command.createdBy(),
                command.updatedBy()
        );
    }
}
