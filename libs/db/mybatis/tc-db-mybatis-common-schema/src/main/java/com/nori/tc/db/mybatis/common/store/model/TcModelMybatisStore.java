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
import com.nori.tc.db.core.model.TcModelSearchCriteria;
import com.nori.tc.db.core.model.store.TcModelStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelMapper;

/**
 * tc_model MyBatis Store 구현체.
 *
 * upsert 주의
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
    public TcModel upsert(UpsertTcModel command) {
        final String modelName = command.modelName();
        final String modelVersion = command.modelVersion();

        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("command.modelName must not be null/blank");
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("command.modelVersion must not be null/blank");
        }
        if (command.commInterface() == null) {
            throw new IllegalArgumentException("command.commInterface must not be null");
        }
        if (command.status() == null) {
            throw new IllegalArgumentException("command.status must not be null");
        }

        try {
            TcModel target = resolveTarget(command);

            final TcModel row = new TcModel(
                    target.modelKey(),
                    modelName,
                    modelVersion,
                    command.commInterface(),
                    command.status(),
                    command.maker(),
                    null,
                    null,
                    target.createdBy(),
                    defaultActor(command.updatedBy())
            );

            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_model insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByNameVersion(modelName, modelVersion)
                    .orElseThrow(() -> new DbAccessException("tc_model upsert succeeded but row not found. nameVersion=" + modelName + "/" + modelVersion));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model upsert duplicate (model_name, model_version). nameVersion=" + modelName + "/" + modelVersion, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model upsert failed. nameVersion=" + modelName + "/" + modelVersion, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model upsert failed (unexpected). nameVersion=" + modelName + "/" + modelVersion, e);
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
        final TcModelSearchCriteria resolvedCriteria = (criteria == null)
                ? TcModelSearchCriteria.empty()
                : criteria;
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            // [FIX] Mapper 시그니처에 맞춰 개별 파라미터로 전달한다.
            // - 기존 구현은 TcModelSearchCriteria를 직접 넘겨 컴파일 오류가 발생했다.
            return mapper.findAll(
                    resolvedCriteria.modelName(),
                    resolvedCriteria.commInterface(),
                    resolvedCriteria.status(),
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

    private String defaultActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "SYSTEM";
        }
        return actor;
    }

    private TcModel resolveTarget(UpsertTcModel command) {
        if (command.modelKey() != null) {
            if (command.modelKey() <= 0) {
                throw new IllegalArgumentException("command.modelKey must be > 0 when provided");
            }
            return mapper.findByModelKey(command.modelKey())
                    .orElseThrow(() -> new DbEntityNotFoundException("tc_model not found for update. modelKey=" + command.modelKey()));
        }

        return mapper.findByNameVersion(command.modelName(), command.modelVersion())
                .orElse(new TcModel(
                        0L,
                        command.modelName(),
                        command.modelVersion(),
                        command.commInterface(),
                        command.status(),
                        command.maker(),
                        null,
                        null,
                        defaultActor(command.createdBy()),
                        defaultActor(command.updatedBy())
                ));
    }
}
