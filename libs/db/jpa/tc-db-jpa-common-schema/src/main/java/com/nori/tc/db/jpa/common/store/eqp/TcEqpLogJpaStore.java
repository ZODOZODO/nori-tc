package com.nori.tc.db.jpa.common.store.eqp;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpLogStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpLog;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.common.eqp.LogLevel;
import com.nori.tc.db.domain.eqp.TcEqpLog;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpLogEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpLogEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpLogJpaRepository;

/**
 * tc_eqp_log JPA Store 구현체.
 */
@Repository
public class TcEqpLogJpaStore implements TcEqpLogStore {

    private final TcEqpLogJpaRepository repository;
    private final TcEqpLogEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpLogJpaStore(TcEqpLogJpaRepository repository, TcEqpLogEntityMapper mapper) {
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
    public TcEqpLog upsert(UpsertTcEqpLog command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        UpsertTcEqpLog normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            final Long eqpKey = normalized.eqpKey();

            final TcEqpLogEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpLogEntity.newEntity(eqpKey));

            mapper.updateEntity(normalized, entity);

            TcEqpLogEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_log] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_log] upsert failed", e);
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
    public Optional<TcEqpLog> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_log] findByEqpKey failed: eqpKey=" + eqpKey, e);
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
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_log] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpLog command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() == null || command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
        if (command.logLevel() == null) throw new IllegalArgumentException("command.logLevel must not be null");
        if (command.logRetentionDays() < 1) {
            throw new IllegalArgumentException("command.logRetentionDays must be >= 1");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private UpsertTcEqpLog normalizeCommand(UpsertTcEqpLog command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcEqpLog(
                command.eqpKey(),
                command.logLevel() == null ? LogLevel.INFO : command.logLevel(),
                command.logRetentionDays() == null ? 30 : command.logRetentionDays(),
                command.logPath()
        );
    }
}
