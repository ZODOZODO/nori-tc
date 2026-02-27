package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelSocketMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.jpa.common.entity.model.TcModelSocketMessageEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelSocketMessageEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelSocketMessageJpaRepository;

/**
 * tc_model_socket_message JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (model_version_key, socket_msg_name) 유니크 키로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelSocketMessageJpaStore implements TcModelSocketMessageStore {

    private final TcModelSocketMessageJpaRepository repository;
    private final TcModelSocketMessageEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcModelSocketMessageJpaStore(
            TcModelSocketMessageJpaRepository repository,
            TcModelSocketMessageEntityMapper mapper
    ) {
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
    public TcModelSocketMessage upsert(UpsertTcModelSocketMessage command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final long modelVersionKey = command.modelVersionKey();
            final String socketMsgName = command.socketMsgName();

            TcModelSocketMessageEntity entity = repository.findByModelVersionKeyAndSocketMsgName(modelVersionKey, socketMsgName)
                    .orElseGet(() -> TcModelSocketMessageEntity.newEntity(modelVersionKey, socketMsgName));

            mapper.updateEntity(command, entity);

            TcModelSocketMessageEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_socket_message] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_socket_message] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param socketMsgName 통신 채널/세션 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSocketMessage> findByModelVersionKeySocketMsgName(long modelVersionKey, String socketMsgName) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        if (socketMsgName == null || socketMsgName.isBlank()) {
            throw new IllegalArgumentException("socketMsgName must not be null/blank");
        }
        try {
            return repository.findByModelVersionKeyAndSocketMsgName(modelVersionKey, socketMsgName).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_socket_message] findByModelVersionKeySocketMsgName failed", e);
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
    public List<TcModelSocketMessage> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelSocketMessageEntity> cq = cb.createQuery(TcModelSocketMessageEntity.class);
            Root<TcModelSocketMessageEntity> root = cq.from(TcModelSocketMessageEntity.class);

            Predicate predicate = cb.equal(root.get("modelVersionKey"), modelVersionKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("socketMsgName")), cb.asc(root.get("socketMsgKey")));

            TypedQuery<TcModelSocketMessageEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_socket_message] findAllByModelVersionKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param socketMsgName 통신 채널/세션 정보
     */
    @Override
    @Transactional
    public void deleteByModelVersionKeySocketMsgName(long modelVersionKey, String socketMsgName) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        if (socketMsgName == null || socketMsgName.isBlank()) {
            throw new IllegalArgumentException("socketMsgName must not be null/blank");
        }
        try {
            repository.findByModelVersionKeyAndSocketMsgName(modelVersionKey, socketMsgName).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_socket_message] deleteByModelVersionKeySocketMsgName failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcModelSocketMessage command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelVersionKey() <= 0) throw new IllegalArgumentException("command.modelVersionKey must be > 0");
        if (command.socketMsgName() == null || command.socketMsgName().isBlank()) {
            throw new IllegalArgumentException("command.socketMsgName must not be null/blank");
        }
    }
}
