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
import com.nori.tc.db.core.model.store.TcModelSecsMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelSecsMessageMapper;

/**
 * tc_model_secs_message MyBatis Store 구현체.
 *
 * upsert 주의
 * - common-schema의 TcModelSecsMessageMapper.xml은 벤더 중립성을 위해 "generated key 반환"을 하지 않는다.
 * - 따라서 insert 후 (model_key, secs_msg_name)으로 재조회하여 secs_msg_key를 확보한다.
 */
@Repository
public class TcModelSecsMessageMybatisStore implements TcModelSecsMessageStore {

    private final TcModelSecsMessageMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelSecsMessageMybatisStore(TcModelSecsMessageMapper mapper) {
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
    public TcModelSecsMessage upsert(UpsertTcModelSecsMessage command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateUpsert(command);

        final Long secsMsgKey = command.secsMsgKey();
        final long modelKey = command.modelKey();
        final String secsMsgName = command.secsMsgName();

        final long resolvedKey = resolveKey(secsMsgKey, modelKey, secsMsgName);

        final TcModelSecsMessage row = new TcModelSecsMessage(
                resolvedKey,
                modelKey,
                secsMsgName,
                command.description(),
                command.dataIndex(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_model_secs_message insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByModelKeyAndName(modelKey, secsMsgName)
                    .orElseThrow(() -> new DbAccessException("tc_model_secs_message upsert succeeded but row not found. modelKey=" + modelKey + ", secsMsgName=" + secsMsgName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_secs_message upsert duplicate (model_key, secs_msg_name). modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message upsert failed. modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message upsert failed (unexpected). modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param secsMsgKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSecsMessage> findBySecsMsgKey(long secsMsgKey) {
        try {
            return mapper.findBySecsMsgKey(secsMsgKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message findBySecsMsgKey failed. secsMsgKey=" + secsMsgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message findBySecsMsgKey failed (unexpected). secsMsgKey=" + secsMsgKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param secsMsgName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSecsMessage> findByModelKeyAndName(long modelKey, String secsMsgName) {
        try {
            return mapper.findByModelKeyAndName(modelKey, secsMsgName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message findByModelKeyAndName failed. modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message findByModelKeyAndName failed (unexpected). modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelSecsMessage> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;
        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message findAllByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message findAllByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param secsMsgKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteBySecsMsgKey(long secsMsgKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteBySecsMsgKey(secsMsgKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message deleteBySecsMsgKey failed. secsMsgKey=" + secsMsgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message deleteBySecsMsgKey failed (unexpected). secsMsgKey=" + secsMsgKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateUpsert(UpsertTcModelSecsMessage command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.secsMsgKey() != null && command.secsMsgKey() <= 0) {
            throw new IllegalArgumentException("command.secsMsgKey must be > 0 when provided");
        }
        if (command.modelKey() <= 0) {
            throw new IllegalArgumentException("command.modelKey must be > 0");
        }
        if (command.secsMsgName() == null || command.secsMsgName().isBlank()) {
            throw new IllegalArgumentException("command.secsMsgName must not be null/blank");
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param secsMsgKey 대상 키 값
     * @param modelKey 대상 키 값
     * @param secsMsgName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    private long resolveKey(Long secsMsgKey, long modelKey, String secsMsgName) {
        if (secsMsgKey != null) {
            if (secsMsgKey <= 0) {
                throw new IllegalArgumentException("secsMsgKey must be > 0 when provided");
            }
            return secsMsgKey;
        }

        return mapper.findByModelKeyAndName(modelKey, secsMsgName)
                .map(TcModelSecsMessage::secsMsgKey)
                .orElse(0L);
    }
}