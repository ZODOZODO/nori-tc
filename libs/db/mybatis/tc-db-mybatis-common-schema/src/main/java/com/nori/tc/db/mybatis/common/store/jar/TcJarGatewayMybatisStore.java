package com.nori.tc.db.mybatis.common.store.jar;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.jar.store.TcJarGatewayStore;
import com.nori.tc.db.core.jar.upsert.UpsertTcJarGateway;
import com.nori.tc.db.domain.jar.TcJarGateway;
import com.nori.tc.db.mybatis.common.mapper.jar.TcJarGatewayMapper;

/**
 * tc_jar_gateway MyBatis Store 구현체.
 *
 * 구현 전략:
 * - update를 먼저 시도하고 영향 행이 0이면 insert를 시도합니다.
 * - 동시성으로 인해 insert가 중복키를 맞으면 update로 재시도합니다.
 */
@Repository
public class TcJarGatewayMybatisStore implements TcJarGatewayStore {

    private final TcJarGatewayMapper mapper;

    /**
     * 생성자 주입.
     *
     * @param mapper MyBatis Mapper
     */
    public TcJarGatewayMybatisStore(TcJarGatewayMapper mapper) {
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
        // 3) update -> insert fallback(upsert)
        // 4) 최종 row 재조회 후 반환
        UpsertTcJarGateway normalized = normalizeCommand(command);
        validateCommand(normalized);

        final Long eqpKey = normalized.eqpKey();
        final TcJarGateway row = new TcJarGateway(
                eqpKey,
                normalized.jarFileName(),
                normalized.jarFile(),
                null,
                null,
                normalized.createdBy(),
                normalized.updatedBy()
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    // 동시성 경합으로 선행 insert가 발생한 경우 update로 정합성 회복
                    mapper.update(row);
                }
            }

            return mapper.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new DbAccessException("tc_jar_gateway upsert succeeded but row not found. eqpKey=" + eqpKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_jar_gateway upsert duplicate key. eqpKey=" + eqpKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_jar_gateway upsert failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_jar_gateway upsert failed (unexpected). eqpKey=" + eqpKey, e);
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
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_jar_gateway findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_jar_gateway findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
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
        try {
            mapper.deleteByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_jar_gateway deleteByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_jar_gateway deleteByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
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
