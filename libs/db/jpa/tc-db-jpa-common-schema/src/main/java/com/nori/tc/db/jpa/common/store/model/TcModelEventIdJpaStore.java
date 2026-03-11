package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelEventIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelEventId;
import com.nori.tc.db.domain.model.TcModelEventId;
import com.nori.tc.db.jpa.common.entity.model.TcModelEventIdEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelEventIdEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelEventIdJpaRepository;

/**
 * tc_model_eventid JPA Store 구현체.
 */
@Repository
public class TcModelEventIdJpaStore implements TcModelEventIdStore {

    private final TcModelEventIdJpaRepository repository;
    private final TcModelEventIdEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcModelEventIdJpaStore(TcModelEventIdJpaRepository repository, TcModelEventIdEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    
    /**
     * DB JPA 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    @Override
    @Transactional
    public TcModelEventId upsert(UpsertTcModelEventId command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        UpsertTcModelEventId normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            final Long modelVersionKey = normalized.modelVersionKey();
            final String eventId = normalized.eventId();

            final TcModelEventIdEntity entity = repository.findByModelVersionKeyAndEventId(modelVersionKey, eventId)
                    .orElseGet(() -> TcModelEventIdEntity.newEntity(modelVersionKey, eventId));

            mapper.updateEntity(normalized, entity);

            TcModelEventIdEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_eventid] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelEventId> findByEventKey(long eventKey) {
        if (eventKey <= 0) {
            throw new IllegalArgumentException("eventKey must be positive");
        }
        try {
            return repository.findById(eventKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] findByEventKey failed: eventKey=" + eventKey, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param eventId 처리할 이벤트 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelEventId> findByModelVersionKeyAndEventId(long modelVersionKey, String eventId) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be positive");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be null/blank");
        }
        try {
            return repository.findByModelVersionKeyAndEventId(modelVersionKey, eventId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] findByModelVersionKeyAndEventId failed: modelVersionKey=" + modelVersionKey + ", eventId=" + eventId, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelEventId> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be positive");
        }
        final PageRequest resolvedPage = (page == null) ? PageRequest.defaultPage() : page;

        try {
            final int pageNumber = resolvedPage.limit() == 0 ? 0 : resolvedPage.offset() / resolvedPage.limit();
            final int offsetRemainder = resolvedPage.limit() == 0 ? 0 : resolvedPage.offset() % resolvedPage.limit();
            final int fetchSize = resolvedPage.limit() + offsetRemainder;
            final org.springframework.data.domain.PageRequest pageable =
                    org.springframework.data.domain.PageRequest.of(pageNumber, fetchSize);

            return repository.findAllByModelVersionKeyOrderByEventKeyAsc(modelVersionKey, pageable).stream()
                    .skip(offsetRemainder)
                    .limit(resolvedPage.limit())
                    .map(mapper::toDomain)
                    .toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] findAllByModelVersionKey failed: modelVersionKey=" + modelVersionKey, e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByEventKey(long eventKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (eventKey <= 0) {
            throw new IllegalArgumentException("eventKey must be positive");
        }
        try {
            repository.deleteById(eventKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] deleteByEventKey failed: eventKey=" + eventKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcModelEventId command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.modelVersionKey() == null || command.modelVersionKey() <= 0) {
            throw new IllegalArgumentException("command.modelVersionKey must be positive");
        }
        if (command.eventId() == null || command.eventId().isBlank()) {
            throw new IllegalArgumentException("command.eventId must not be null/blank");
        }
        if (command.enabled() == null) {
            throw new IllegalArgumentException("command.enabled must not be null");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private UpsertTcModelEventId normalizeCommand(UpsertTcModelEventId command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcModelEventId(
                command.modelVersionKey(),
                command.eventId(),
                command.reportId(),
                command.description(),
                command.enabled() == null ? Boolean.FALSE : command.enabled()
        );
    }
}
