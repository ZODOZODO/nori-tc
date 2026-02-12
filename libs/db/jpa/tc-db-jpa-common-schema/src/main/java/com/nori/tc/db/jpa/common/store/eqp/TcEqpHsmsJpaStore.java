package com.nori.tc.db.jpa.common.store.eqp;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpHsms;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpHsmsEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpHsmsEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpHsmsJpaRepository;

/**
 * tc_eqp_hsms JPA Store 구현체.
 *
 * <p>
 * <b>특이사항:</b>
 * HSMS 설정값(t3~t8, interval 등)이 많아 수동 매핑 시 실수가 잦은 영역입니다.
 * MapStruct를 통해 Command -> Entity 매핑을 100% 자동화했습니다.
 * </p>
 */
@Repository
public class TcEqpHsmsJpaStore implements TcEqpHsmsStore {

    private final TcEqpHsmsJpaRepository repository;
    private final TcEqpHsmsEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpHsmsJpaStore(TcEqpHsmsJpaRepository repository, TcEqpHsmsEntityMapper mapper) {
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
    public TcEqpHsms upsert(UpsertTcEqpHsms command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final long eqpKey = command.eqpKey();

            // 1. 조회 또는 신규 생성
            final TcEqpHsmsEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpHsmsEntity.newEntity(eqpKey));

            // 2. [MapStruct] 전체 필드 자동 매핑
            mapper.updateEntity(command, entity);

            // 3. 저장 및 반환
            TcEqpHsmsEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_hsms] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] upsert failed", e);
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
    public Optional<TcEqpHsms> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] findByEqpKey failed: eqpKey=" + eqpKey, e);
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
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpHsms command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.deviceId() < 0) throw new IllegalArgumentException("command.deviceId must be >= 0");
        if (command.connectionMode() == null || command.connectionMode().isBlank()) {
            throw new IllegalArgumentException("command.connectionMode must not be null/blank");
        }
        if (command.t3Timeout() <= 0) throw new IllegalArgumentException("command.t3Timeout must be > 0");
        if (command.t5Timeout() <= 0) throw new IllegalArgumentException("command.t5Timeout must be > 0");
        if (command.t6Timeout() <= 0) throw new IllegalArgumentException("command.t6Timeout must be > 0");
        if (command.t7Timeout() <= 0) throw new IllegalArgumentException("command.t7Timeout must be > 0");
        if (command.t8Timeout() <= 0) throw new IllegalArgumentException("command.t8Timeout must be > 0");
        if (command.linkTestInterval() <= 0) throw new IllegalArgumentException("command.linkTestInterval must be > 0");
        if (command.maxMsgBytes() <= 0) throw new IllegalArgumentException("command.maxMsgBytes must be > 0");
    }
}
