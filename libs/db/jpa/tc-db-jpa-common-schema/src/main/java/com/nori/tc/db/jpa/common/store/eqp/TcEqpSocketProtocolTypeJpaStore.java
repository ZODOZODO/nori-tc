package com.nori.tc.db.jpa.common.store.eqp;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpSocketProtocolTypeStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocketProtocolType;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpSocketProtocolTypeEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpSocketProtocolTypeEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpSocketProtocolTypeJpaRepository;

/**
 * tc_eqp_socket_protocol_type JPA Store 구현체.
 */
@Repository
public class TcEqpSocketProtocolTypeJpaStore implements TcEqpSocketProtocolTypeStore {

    private final TcEqpSocketProtocolTypeJpaRepository repository;
    private final TcEqpSocketProtocolTypeEntityMapper mapper;
    private final EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     * @param em DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpSocketProtocolTypeJpaStore(TcEqpSocketProtocolTypeJpaRepository repository,
                                          TcEqpSocketProtocolTypeEntityMapper mapper,
                                          EntityManager em) {
        this.repository = repository;
        this.mapper = mapper;
        this.em = em;
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
    public TcEqpSocketProtocolType upsert(UpsertTcEqpSocketProtocolType command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final String socketProtocolType = command.socketProtocolType();

            final TcEqpSocketProtocolTypeEntity entity = repository.findById(socketProtocolType)
                    .orElseGet(() -> TcEqpSocketProtocolTypeEntity.newEntity(socketProtocolType));

            mapper.updateEntity(command, entity);

            TcEqpSocketProtocolTypeEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_socket_protocol_type] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket_protocol_type] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpSocketProtocolType> findBySocketProtocolType(String socketProtocolType) {
        if (socketProtocolType == null || socketProtocolType.isBlank()) {
            throw new IllegalArgumentException("socketProtocolType must not be null/blank");
        }

        try {
            return repository.findById(socketProtocolType).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket_protocol_type] findBySocketProtocolType failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcEqpSocketProtocolType> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            TypedQuery<TcEqpSocketProtocolTypeEntity> query = em.createQuery(
                    "SELECT e FROM TcEqpSocketProtocolTypeEntity e ORDER BY e.socketProtocolType",
                    TcEqpSocketProtocolTypeEntity.class
            );
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket_protocol_type] findAll failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     */
    @Override
    @Transactional
    public void deleteBySocketProtocolType(String socketProtocolType) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (socketProtocolType == null || socketProtocolType.isBlank()) {
            throw new IllegalArgumentException("socketProtocolType must not be null/blank");
        }

        try {
            repository.deleteById(socketProtocolType);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket_protocol_type] deleteBySocketProtocolType failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpSocketProtocolType command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.socketProtocolType() == null || command.socketProtocolType().isBlank()) {
            throw new IllegalArgumentException("command.socketProtocolType must not be null/blank");
        }
        if (command.socketProtocolTypeName() == null || command.socketProtocolTypeName().isBlank()) {
            throw new IllegalArgumentException("command.socketProtocolTypeName must not be null/blank");
        }
    }
}
