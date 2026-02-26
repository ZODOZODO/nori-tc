package com.nori.tc.db.jpa.common.store.eqp;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocket;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpSocketEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpSocketEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpSocketJpaRepository;

/**
 * tc_eqp_socket JPA Store 구현체.
 *
 * <p>
 * <b>Charset 정책:</b>
 * DB 컬럼은 NOT NULL입니다. 커맨드로 들어온 charset이 null/blank인 경우,
 * 애플리케이션 레벨에서 기본값(UTF-8)을 강제하여 DB 제약을 만족시킵니다.
 * </p>
 */
@Repository
public class TcEqpSocketJpaStore implements TcEqpSocketStore {

    private static final String DEFAULT_CHARSET = "UTF-8";

    private final TcEqpSocketJpaRepository repository;
    private final TcEqpSocketEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpSocketJpaStore(TcEqpSocketJpaRepository repository, TcEqpSocketEntityMapper mapper) {
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
    public TcEqpSocket upsert(UpsertTcEqpSocket command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final long eqpKey = command.eqpKey();

            final TcEqpSocketEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpSocketEntity.newEntity(eqpKey));

            // 1. [MapStruct] 일반 필드 매핑
            mapper.updateEntity(command, entity);

            // 2. [Manual Logic] Charset 기본값 처리
            final String charset = (command.charset() == null || command.charset().isBlank())
                    ? DEFAULT_CHARSET
                    : command.charset();
            entity.setCharset(charset);

            // 3. 저장 및 반환
            TcEqpSocketEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_socket] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket] upsert failed", e);
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
    public Optional<TcEqpSocket> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket] findByEqpKey failed: eqpKey=" + eqpKey, e);
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
            throw new DbAccessException("[tc_eqp_socket] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpSocket command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
        if (command.socketProtocolType() == null || command.socketProtocolType().isBlank()) throw new IllegalArgumentException("command.socketProtocolType must not be null/blank");
    }
}
