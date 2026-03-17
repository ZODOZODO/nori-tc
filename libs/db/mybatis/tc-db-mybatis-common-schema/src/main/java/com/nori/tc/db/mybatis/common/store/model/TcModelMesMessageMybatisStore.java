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
import com.nori.tc.db.core.model.store.TcModelMesMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMesMessage;
import com.nori.tc.db.domain.model.TcModelMesMessage;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelMesMessageMapper;

/**
 * tc_model_mes_message MyBatis Store 구현체.
 *
 * upsert 주의:
 * - common-schema의 TcModelMesMessageMapper.xml은 벤더 중립성을 위해 "generated key 반환"을 하지 않는다.
 * - 따라서 insert 후 (model_version_key, mes_msg_name)으로 재조회하여 mes_msg_key를 확보한다.
 */
@Repository
public class TcModelMesMessageMybatisStore implements TcModelMesMessageStore {

    private final TcModelMesMessageMapper mapper;

    /**
     * 의존성 주입 생성자.
     *
     * @param mapper MES 메시지 MyBatis Mapper
     */
    public TcModelMesMessageMybatisStore(TcModelMesMessageMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * MES 메시지 upsert.
     *
     * <p>mes_msg_key로 기존 레코드를 먼저 UPDATE 시도하고, 영향 행이 없으면 INSERT를 수행합니다.</p>
     *
     * @param command upsert 요청 정보
     * @return upsert 후 상태의 TcModelMesMessage
     */
    @Override
    @Transactional
    public TcModelMesMessage upsert(UpsertTcModelMesMessage command) {
        validateUpsert(command);

        final Long mesMsgKey = command.mesMsgKey();
        final long modelVersionKey = command.modelVersionKey();
        final String mesMsgName = command.mesMsgName();

        final long resolvedKey = resolveKey(mesMsgKey, modelVersionKey, mesMsgName);

        final TcModelMesMessage row = new TcModelMesMessage(
                resolvedKey,
                modelVersionKey,
                mesMsgName,
                command.description(),
                command.dataIndex(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_model_mes_message insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByModelVersionKeyAndName(modelVersionKey, mesMsgName)
                    .orElseThrow(() -> new DbAccessException("tc_model_mes_message upsert succeeded but row not found. modelVersionKey=" + modelVersionKey + ", mesMsgName=" + mesMsgName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_mes_message upsert duplicate (model_version_key, mes_msg_name). modelVersionKey=" + modelVersionKey + ", mesMsgName=" + mesMsgName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mes_message upsert failed. modelVersionKey=" + modelVersionKey + ", mesMsgName=" + mesMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mes_message upsert failed (unexpected). modelVersionKey=" + modelVersionKey + ", mesMsgName=" + mesMsgName, e);
        }
    }

    /**
     * PK로 MES 메시지 단건 조회.
     *
     * @param mesMsgKey MES 메시지 PK
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMesMessage> findByMesMsgKey(long mesMsgKey) {
        try {
            return mapper.findByMesMsgKey(mesMsgKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mes_message findByMesMsgKey failed. mesMsgKey=" + mesMsgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mes_message findByMesMsgKey failed (unexpected). mesMsgKey=" + mesMsgKey, e);
        }
    }

    /**
     * (model_version_key, mes_msg_name) 유니크 키로 단건 조회.
     *
     * @param modelVersionKey 모델 버전 키
     * @param mesMsgName      MES 메시지 이름
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMesMessage> findByModelVersionKeyAndName(long modelVersionKey, String mesMsgName) {
        try {
            return mapper.findByModelVersionKeyAndName(modelVersionKey, mesMsgName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mes_message findByModelVersionKeyAndName failed. modelVersionKey=" + modelVersionKey + ", mesMsgName=" + mesMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mes_message findByModelVersionKeyAndName failed (unexpected). modelVersionKey=" + modelVersionKey + ", mesMsgName=" + mesMsgName, e);
        }
    }

    /**
     * 특정 모델(model_version_key)의 MES 메시지 목록 조회 (페이징).
     *
     * @param modelVersionKey 모델 버전 키
     * @param page            페이징 조건
     * @return MES 메시지 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelMesMessage> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;
        try {
            return mapper.findAllByModelVersionKey(modelVersionKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mes_message findAllByModelVersionKey failed. modelVersionKey=" + modelVersionKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mes_message findAllByModelVersionKey failed (unexpected). modelVersionKey=" + modelVersionKey, e);
        }
    }

    /**
     * PK로 MES 메시지 삭제.
     *
     * @param mesMsgKey MES 메시지 PK
     */
    @Override
    @Transactional
    public void deleteByMesMsgKey(long mesMsgKey) {
        try {
            mapper.deleteByMesMsgKey(mesMsgKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_mes_message deleteByMesMsgKey failed. mesMsgKey=" + mesMsgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_mes_message deleteByMesMsgKey failed (unexpected). mesMsgKey=" + mesMsgKey, e);
        }
    }

    /**
     * upsert command 유효성 검증.
     *
     * @param command upsert 요청 정보
     */
    private void validateUpsert(UpsertTcModelMesMessage command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.mesMsgKey() != null && command.mesMsgKey() <= 0) {
            throw new IllegalArgumentException("command.mesMsgKey must be > 0 when provided");
        }
        if (command.modelVersionKey() <= 0) {
            throw new IllegalArgumentException("command.modelVersionKey must be > 0");
        }
        if (command.mesMsgName() == null || command.mesMsgName().isBlank()) {
            throw new IllegalArgumentException("command.mesMsgName must not be null/blank");
        }
    }

    /**
     * upsert 대상 PK를 결정합니다.
     *
     * <p>mes_msg_key가 지정된 경우 그 값을 사용하고, 없으면 유니크 키로 기존 레코드를 조회하여 PK를 반환합니다.
     * 신규 레코드인 경우 0을 반환하며, UPDATE WHERE mes_msg_key = 0 조건은 매치되지 않으므로 INSERT로 분기됩니다.</p>
     *
     * @param mesMsgKey       지정된 MES 메시지 PK (null 가능)
     * @param modelVersionKey 모델 버전 키
     * @param mesMsgName      MES 메시지 이름
     * @return 기존 레코드의 PK 또는 0 (신규)
     */
    private long resolveKey(Long mesMsgKey, long modelVersionKey, String mesMsgName) {
        if (mesMsgKey != null) {
            if (mesMsgKey <= 0) {
                throw new IllegalArgumentException("mesMsgKey must be > 0 when provided");
            }
            return mesMsgKey;
        }

        return mapper.findByModelVersionKeyAndName(modelVersionKey, mesMsgName)
                .map(TcModelMesMessage::mesMsgKey)
                .orElse(0L);
    }
}
