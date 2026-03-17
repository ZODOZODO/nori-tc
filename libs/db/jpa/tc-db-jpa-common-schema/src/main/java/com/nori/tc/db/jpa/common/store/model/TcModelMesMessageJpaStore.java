package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.model.store.TcModelMesMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMesMessage;
import com.nori.tc.db.domain.model.TcModelMesMessage;
import com.nori.tc.db.jpa.common.entity.model.TcModelMesMessageEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelMesMessageEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelMesMessageJpaRepository;

/**
 * tc_model_mes_message JPA Store 구현체.
 *
 * <p>
 * <b>주요 기능:</b>
 * <ul>
 * <li><b>Upsert:</b> MES 메시지 키 또는 유니크 키로 존재 여부를 확인한 뒤 생성/갱신을 수행합니다.</li>
 * <li><b>모델별 메시지 조회:</b> model_version_key 기반 목록 조회 및 유니크 키 조회를 지원합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelMesMessageJpaStore implements TcModelMesMessageStore {

    private final TcModelMesMessageJpaRepository repository;
    private final TcModelMesMessageEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    /**
     * 의존성 주입 생성자.
     *
     * @param repository JPA Repository
     * @param mapper     MapStruct Mapper
     */
    public TcModelMesMessageJpaStore(
            TcModelMesMessageJpaRepository repository,
            TcModelMesMessageEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * MES 메시지 upsert.
     *
     * <p>mes_msg_key가 있으면 PK 기반으로 갱신, 없으면 유니크 키로 조회 후 갱신/생성합니다.</p>
     *
     * @param command upsert 요청 정보
     * @return upsert 후 상태의 TcModelMesMessage
     */
    @Override
    @Transactional
    public TcModelMesMessage upsert(UpsertTcModelMesMessage command) {
        validateUpsert(command);

        try {
            TcModelMesMessageEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcModelMesMessageEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_mes_message] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mes_message] upsert failed", e);
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
        if (mesMsgKey <= 0) {
            throw new IllegalArgumentException("mesMsgKey must be > 0");
        }
        try {
            return repository.findById(mesMsgKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mes_message] findByMesMsgKey failed: mesMsgKey=" + mesMsgKey, e);
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
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        if (mesMsgName == null || mesMsgName.isBlank()) {
            throw new IllegalArgumentException("mesMsgName must not be null/blank");
        }
        try {
            return repository.findByModelVersionKeyAndMesMsgName(modelVersionKey, mesMsgName).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mes_message] findByModelVersionKeyAndName failed", e);
        }
    }

    /**
     * 특정 모델(model_version_key)의 MES 메시지 목록 조회 (페이징 지원).
     *
     * @param modelVersionKey 모델 버전 키
     * @param page            페이징 조건
     * @return MES 메시지 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelMesMessage> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;
        try {
            TypedQuery<TcModelMesMessageEntity> query = em.createQuery(
                    "SELECT e FROM TcModelMesMessageEntity e WHERE e.modelVersionKey = :modelVersionKey ORDER BY e.mesMsgKey ASC",
                    TcModelMesMessageEntity.class
            );
            query.setParameter("modelVersionKey", modelVersionKey);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mes_message] findAllByModelVersionKey failed: modelVersionKey=" + modelVersionKey, e);
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
        if (mesMsgKey <= 0) {
            throw new IllegalArgumentException("mesMsgKey must be > 0");
        }
        try {
            repository.deleteById(mesMsgKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mes_message] deleteByMesMsgKey failed: mesMsgKey=" + mesMsgKey, e);
        }
    }

    /**
     * upsert command 유효성 검증.
     *
     * @param command upsert 요청 정보
     */
    private void validateUpsert(UpsertTcModelMesMessage command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.mesMsgKey() != null && command.mesMsgKey() <= 0) {
            throw new IllegalArgumentException("command.mesMsgKey must be > 0 when provided");
        }
        if (command.modelVersionKey() <= 0) throw new IllegalArgumentException("command.modelVersionKey must be > 0");
        if (command.mesMsgName() == null || command.mesMsgName().isBlank()) {
            throw new IllegalArgumentException("command.mesMsgName must not be null/blank");
        }
    }

    /**
     * upsert 대상 엔티티를 결정합니다.
     *
     * <p>mes_msg_key가 지정되면 해당 PK로 조회, 없으면 유니크 키로 조회 후 없을 경우 신규 엔티티를 반환합니다.</p>
     *
     * @param command upsert 요청 정보
     * @return 기존 또는 신규 엔티티
     */
    private TcModelMesMessageEntity resolveEntity(UpsertTcModelMesMessage command) {
        if (command.mesMsgKey() != null) {
            return repository.findById(command.mesMsgKey())
                    .orElseThrow(() -> new DbEntityNotFoundException("[tc_model_mes_message] not found: mesMsgKey=" + command.mesMsgKey()));
        }

        return repository.findByModelVersionKeyAndMesMsgName(command.modelVersionKey(), command.mesMsgName())
                .orElseGet(() -> TcModelMesMessageEntity.newEntity(command.modelVersionKey(), command.mesMsgName()));
    }
}
