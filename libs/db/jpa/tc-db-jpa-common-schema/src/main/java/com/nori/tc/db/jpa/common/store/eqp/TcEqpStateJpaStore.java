package com.nori.tc.db.jpa.common.store.eqp;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpStateStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpState;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpState;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpStateEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpStateEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpStateJpaRepository;

/**
 * tc_eqp_state JPA Store 구현체.
 */
@Repository
public class TcEqpStateJpaStore implements TcEqpStateStore {

    private final TcEqpStateJpaRepository repository;
    private final TcEqpStateEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpStateJpaStore(TcEqpStateJpaRepository repository, TcEqpStateEntityMapper mapper) {
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
    public TcEqpState upsert(UpsertTcEqpState command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final long eqpKey = command.eqpKey();

            final TcEqpStateEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpStateEntity.newEntity(eqpKey));

            mapper.updateEntity(command, entity);

            TcEqpStateEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_state] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpState> findByEqpKey(long eqpKey) {
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state] findByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     */
    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpState command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
    }
}
