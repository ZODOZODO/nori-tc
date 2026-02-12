package com.nori.tc.db.mybatis.common.store.model;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelEventIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelEventId;
import com.nori.tc.db.domain.model.TcModelEventId;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelEventIdMapper;

/**
 * tc_model_eventid MyBatis Store 구현체.
 */
@Repository
public class TcModelEventIdMybatisStore implements TcModelEventIdStore {

    private final TcModelEventIdMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelEventIdMybatisStore(TcModelEventIdMapper mapper) {
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
    public TcModelEventId upsert(UpsertTcModelEventId command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        UpsertTcModelEventId normalized = normalizeCommand(command);
        validateCommand(normalized);

        final long modelKey = normalized.modelKey();
        final String eventId = normalized.eventId();

        final TcModelEventId row = new TcModelEventId(
                0L,
                modelKey,
                eventId,
                normalized.reportId(),
                normalized.enabled(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.update(row);
                }
            }

            return mapper.findByModelKeyAndEventId(modelKey, eventId)
                    .orElseThrow(() -> new DbAccessException("tc_model_eventid upsert succeeded but row not found. modelKey=" + modelKey + ", eventId=" + eventId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_eventid upsert duplicate key. modelKey=" + modelKey + ", eventId=" + eventId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_eventid upsert failed. modelKey=" + modelKey + ", eventId=" + eventId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_eventid upsert failed (unexpected). modelKey=" + modelKey + ", eventId=" + eventId, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelEventId> findByEventKey(long eventKey) {
        try {
            return mapper.findByEventKey(eventKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_eventid findByEventKey failed. eventKey=" + eventKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_eventid findByEventKey failed (unexpected). eventKey=" + eventKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param eventId 처리할 이벤트 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelEventId> findByModelKeyAndEventId(long modelKey, String eventId) {
        try {
            return mapper.findByModelKeyAndEventId(modelKey, eventId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_eventid findByModelKeyAndEventId failed. modelKey=" + modelKey + ", eventId=" + eventId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_eventid findByModelKeyAndEventId failed (unexpected). modelKey=" + modelKey + ", eventId=" + eventId, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByEventKey(long eventKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByEventKey(eventKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_eventid deleteByEventKey failed. eventKey=" + eventKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_eventid deleteByEventKey failed (unexpected). eventKey=" + eventKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcModelEventId command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.modelKey() == null || command.modelKey() <= 0) {
            throw new IllegalArgumentException("command.modelKey must be positive");
        }
        if (command.eventId() == null || command.eventId().isBlank()) {
            throw new IllegalArgumentException("command.eventId must not be null/blank");
        }
        if (command.enabled() == null) {
            throw new IllegalArgumentException("command.enabled must not be null");
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private UpsertTcModelEventId normalizeCommand(UpsertTcModelEventId command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcModelEventId(
                command.modelKey(),
                command.eventId(),
                command.reportId(),
                command.enabled() == null ? Boolean.FALSE : command.enabled()
        );
    }
}
