package com.nori.tc.db.mybatis.common.store.eqp;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpParamVersionStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParamVersion;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpParamVersion;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpParamVersionMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * tc_eqp_param_version MyBatis Store 구현체입니다.
 */
@Repository
public class TcEqpParamVersionMybatisStore implements TcEqpParamVersionStore {

    private final TcEqpParamVersionMapper mapper;

    public TcEqpParamVersionMybatisStore(final TcEqpParamVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpParamVersion upsert(final UpsertTcEqpParamVersion command) {
        final TcEqpParamVersion row = new TcEqpParamVersion(
                0L,
                command.eqpKey(),
                command.paramVersion(),
                command.versionDescription(),
                null,
                null,
                command.createdBy(),
                command.updatedBy()
        );

        try {
            final int updated = mapper.updateByUniqueKey(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException duplicateKeyException) {
                    mapper.updateByUniqueKey(row);
                }
            }

            return mapper.findByEqpKeyAndParamVersion(command.eqpKey(), command.paramVersion())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_eqp_param_version upsert succeeded but row not found. eqpKey="
                                    + command.eqpKey() + ", version=" + command.paramVersion()
                    ));
        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_eqp_param_version upsert duplicate key. eqpKey=" + command.eqpKey() + ", version=" + command.paramVersion(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_eqp_param_version upsert failed. eqpKey=" + command.eqpKey() + ", version=" + command.paramVersion(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_eqp_param_version upsert failed (unexpected). eqpKey=" + command.eqpKey() + ", version=" + command.paramVersion(),
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpParamVersion> findByEqpKeyAndParamVersion(final long eqpKey, final String paramVersion) {
        try {
            return mapper.findByEqpKeyAndParamVersion(eqpKey, paramVersion);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_eqp_param_version findByEqpKeyAndParamVersion failed. eqpKey=" + eqpKey + ", version=" + paramVersion,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_eqp_param_version findByEqpKeyAndParamVersion failed (unexpected). eqpKey=" + eqpKey + ", version=" + paramVersion,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpParamVersion> findAllByEqpKey(final long eqpKey, final PageRequest page) {
        final PageRequest effectivePage = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByEqpKey(eqpKey, effectivePage.offset(), effectivePage.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_param_version findAllByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_param_version findAllByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteAllByEqpKey(final long eqpKey) {
        try {
            mapper.deleteAllByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_param_version deleteAllByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_param_version deleteAllByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }
}
