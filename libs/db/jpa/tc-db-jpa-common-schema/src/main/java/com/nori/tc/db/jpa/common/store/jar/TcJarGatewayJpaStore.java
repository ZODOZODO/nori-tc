package com.nori.tc.db.jpa.common.store.jar;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.jar.store.TcJarGatewayStore;
import com.nori.tc.db.core.jar.upsert.UpsertTcJarGateway;
import com.nori.tc.db.domain.jar.TcJarGateway;
import com.nori.tc.db.jpa.common.entity.jar.TcJarGatewayEntity;
import com.nori.tc.db.jpa.common.mapper.jar.TcJarGatewayEntityMapper;
import com.nori.tc.db.jpa.common.repository.jar.TcJarGatewayJpaRepository;

/**
 * tc_jar_gateway JPA Store 구현체.
 *
 * 구현 전략:
 * - PK(eqp_key) 기준 1:1 테이블이므로 findById + save 조합으로 upsert를 수행합니다.
 * - 생성자/수정자 기본값은 normalize 단계와 엔티티 lifecycle에서 이중 방어합니다.
 */
@Repository
public class TcJarGatewayJpaStore implements TcJarGatewayStore {

    private final TcJarGatewayJpaRepository repository;
    private final TcJarGatewayEntityMapper mapper;

    /**
     * 생성자 주입.
     *
     * @param repository JPA Repository
     * @param mapper Entity/Domain 매퍼
     */
    public TcJarGatewayJpaStore(
            TcJarGatewayJpaRepository repository,
            TcJarGatewayEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * tc_jar_gateway를 upsert 합니다.
     *
     * @param command upsert 입력 값
     * @return upsert 결과
     */
    @Override
    @Transactional
    public TcJarGateway upsert(UpsertTcJarGateway command) {
        // 1) 기본값 보정
        // 2) 입력값 검증
        // 3) PK 기준 조회 후 신규/수정 분기
        // 4) save 후 Domain으로 반환
        UpsertTcJarGateway normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            final Long eqpKey = normalized.eqpKey();

            Optional<TcJarGatewayEntity> found = repository.findById(eqpKey);
            TcJarGatewayEntity entity = found.orElseGet(() -> TcJarGatewayEntity.newEntity(eqpKey));

            mapper.updateEntity(normalized, entity);
            if (found.isEmpty()) {
                entity.setCreatedBy(normalized.createdBy());
            }
            entity.setUpdatedBy(normalized.updatedBy());

            TcJarGatewayEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_jar_gateway] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_jar_gateway] upsert failed", e);
        }
    }

    /**
     * eqp_key 기준으로 단건 조회합니다.
     *
     * @param eqpKey 설비 키
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcJarGateway> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_jar_gateway] findByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    /**
     * eqp_key 기준으로 단건 삭제합니다.
     *
     * @param eqpKey 설비 키
     */
    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // 멱등 삭제 정책: 대상이 없어도 성공으로 간주
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_jar_gateway] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    /**
     * 입력 유효성을 검증합니다.
     *
     * @param command 검증 대상 커맨드
     */
    private void validateCommand(UpsertTcJarGateway command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.eqpKey() == null || command.eqpKey() <= 0) {
            throw new IllegalArgumentException("command.eqpKey must be positive");
        }
        if (command.jarFileName() == null || command.jarFileName().isBlank()) {
            throw new IllegalArgumentException("command.jarFileName must not be null/blank");
        }
        if (command.jarFileName().length() > 255) {
            throw new IllegalArgumentException("command.jarFileName length must be <= 255");
        }
        if (command.jarFile() == null || command.jarFile().length == 0) {
            throw new IllegalArgumentException("command.jarFile must not be null/empty");
        }
        if (command.createdBy() == null || command.createdBy().isBlank()) {
            throw new IllegalArgumentException("command.createdBy must not be null/blank");
        }
        if (command.updatedBy() == null || command.updatedBy().isBlank()) {
            throw new IllegalArgumentException("command.updatedBy must not be null/blank");
        }
    }

    /**
     * 기본값/문자열 정규화를 수행합니다.
     *
     * @param command 원본 커맨드
     * @return 정규화된 커맨드
     */
    private UpsertTcJarGateway normalizeCommand(UpsertTcJarGateway command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcJarGateway(
                command.eqpKey(),
                command.jarFileName() == null ? null : command.jarFileName().trim(),
                command.jarFile(),
                normalizeActor(command.createdBy()),
                normalizeActor(command.updatedBy())
        );
    }

    /**
     * 감사 사용자명을 정규화합니다.
     *
     * @param actor 사용자명
     * @return null/blank면 SYSTEM, 아니면 trim 결과
     */
    private String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "SYSTEM";
        }
        return actor.trim();
    }
}
