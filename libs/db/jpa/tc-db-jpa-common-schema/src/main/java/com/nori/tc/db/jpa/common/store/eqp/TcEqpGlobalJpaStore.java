package com.nori.tc.db.jpa.common.store.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpGlobalStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpGlobal;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpGlobal;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpGlobalEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpGlobalEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpGlobalJpaRepository;

/**
 * tc_eqp_global JPA Store 구현체.
 *
 * - 유니크 키(eqp_key, param_name) 기반 upsert 제공
 */
@Repository
public class TcEqpGlobalJpaStore implements TcEqpGlobalStore {

    private final TcEqpGlobalJpaRepository repository;
    private final TcEqpGlobalEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpGlobalJpaStore(TcEqpGlobalJpaRepository repository, TcEqpGlobalEntityMapper mapper) {
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
    public TcEqpGlobal upsert(UpsertTcEqpGlobal command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final long eqpKey = command.eqpKey();
            final String paramName = command.paramName();

            final TcEqpGlobalEntity entity = repository.findByEqpKeyAndParamName(eqpKey, paramName)
                    .orElseGet(() -> TcEqpGlobalEntity.newEntity(eqpKey, paramName));

            mapper.updateEntity(command, entity);

            TcEqpGlobalEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_global] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_global] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpGlobal> findByEqpKeyAndParamName(long eqpKey, String paramName) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName must not be null/blank");
        }
        try {
            return repository.findByEqpKeyAndParamName(eqpKey, paramName).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_global] findByEqpKeyAndParamName failed: eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcEqpGlobal> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            return repository.findByEqpKey(eqpKey).stream()
                    .map(mapper::toDomain)
                    .toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_global] findByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByEqpKeyAndParamName(long eqpKey, String paramName) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName must not be null/blank");
        }
        try {
            repository.deleteByEqpKeyAndParamName(eqpKey, paramName);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_global] deleteByEqpKeyAndParamName failed: eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpGlobal command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
        if (command.paramName() == null || command.paramName().isBlank()) throw new IllegalArgumentException("command.paramName must not be null/blank");
    }
}
