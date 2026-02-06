package com.nori.tc.db.mybatis.common.store.work;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.work.store.TcWorkParamStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkParam;
import com.nori.tc.db.domain.work.TcWorkParam;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkParamMapper;

/**
 * tc_work_param MyBatis Store 구현체.
 *
 * <p>
 * - Unique(work_key, param_name) 기준 upsert를 제공한다.
 * - 목록 조회는 반드시 DB 페이징을 적용한다.
 * - insert 이후 생성된 PK는 재조회로 확보한다.
 * </p>
 */
@Repository
public class TcWorkParamMybatisStore implements TcWorkParamStore {

    private final TcWorkParamMapper mapper;

    public TcWorkParamMybatisStore(TcWorkParamMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkParam upsert(UpsertTcWorkParam command) {
        final long workKey = command.workKey();
        final String paramName = command.paramName();

        // PK는 IDENTITY라서 0L로 채우고, 나머지 값만 전달한다.
        final TcWorkParam row = new TcWorkParam(
                0L,
                workKey,
                paramName,
                command.paramValue(),
                null
        );

        try {
            int updated = mapper.updateByUniqueKey(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    // 동시성 상황에서 insert가 실패할 수 있으므로 즉시 update로 보정한다.
                    mapper.updateByUniqueKey(row);
                }
            }

            return mapper.findByWorkKeyAndName(workKey, paramName)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_work_param upsert succeeded but row not found. workKey=" + workKey + ", param=" + paramName
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_work_param upsert duplicate key. workKey=" + workKey + ", param=" + paramName,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_param upsert failed. workKey=" + workKey + ", param=" + paramName,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_param upsert failed (unexpected). workKey=" + workKey + ", param=" + paramName,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkParam> findByWorkKeyAndName(long workKey, String paramName) {
        try {
            return mapper.findByWorkKeyAndName(workKey, paramName);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_param findByWorkKeyAndName failed. workKey=" + workKey + ", param=" + paramName,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_param findByWorkKeyAndName failed (unexpected). workKey=" + workKey + ", param=" + paramName,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkParam> findAllByWorkKey(long workKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByWorkKey(workKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_param findAllByWorkKey failed. workKey=" + workKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_param findAllByWorkKey failed (unexpected). workKey=" + workKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkParamKey(long workParamKey) {
        try {
            mapper.deleteByWorkParamKey(workParamKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_param deleteByWorkParamKey failed. workParamKey=" + workParamKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_param deleteByWorkParamKey failed (unexpected). workParamKey=" + workParamKey, e);
        }
    }
}
