package com.nori.tc.db.jpa.common.store.model;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;

/**
 * {@code tc_model} + {@code tc_model_version} 조인 관점의 JPA Store 구현입니다.
 *
 * <p>
 * 핵심 목적은 MyBatis 구현과 동일한 스키마/동작 정합성을 유지하는 것입니다.
 * 특히 {@link UpsertTcModel#modelKey()}는 과거 호환성 때문에
 * "model_key"가 아니라 "model_version_key" 의미로 해석합니다.
 * </p>
 */
@Repository
public class TcModelJpaStore implements TcModelStore {

    private static final Logger log = LoggerFactory.getLogger(TcModelJpaStore.class);

    /**
     * TcModel 도메인 생성자 순서와 동일한 공통 조회 컬럼 목록입니다.
     */
    private static final String TC_MODEL_COLUMNS = """
            mv.model_version_key,
            m.model_key,
            m.model_name,
            mv.model_version,
            m.comm_interface,
            mv.status,
            mv.description,
            m.maker,
            mv.created_at,
            mv.updated_at,
            mv.created_by,
            mv.updated_by
            """;

    /**
     * model_version_key 단건 조회 SQL입니다.
     */
    private static final String FIND_BY_MODEL_VERSION_KEY_SQL = """
            SELECT
            """ + TC_MODEL_COLUMNS + """
              FROM tc_model_version mv
              JOIN tc_model m ON m.model_key = mv.model_key
             WHERE mv.model_version_key = :modelVersionKey
            """;

    /**
     * (model_name, model_version) 단건 조회 SQL입니다.
     */
    private static final String FIND_BY_NAME_VERSION_SQL = """
            SELECT
            """ + TC_MODEL_COLUMNS + """
              FROM tc_model_version mv
              JOIN tc_model m ON m.model_key = mv.model_key
             WHERE m.model_name = :modelName
               AND mv.model_version = :modelVersion
            """;

    /**
     * 페이지 조회 SQL입니다.
     */
    private static final String FIND_ALL_SQL = """
            SELECT
            """ + TC_MODEL_COLUMNS + """
              FROM tc_model_version mv
              JOIN tc_model m ON m.model_key = mv.model_key
             ORDER BY mv.model_version_key DESC
             LIMIT :limit OFFSET :offset
            """;

    /**
     * 신규/기존 모델에 대한 업서트(SQL CTE) 후 모델 버전을 업서트하는 SQL입니다.
     */
    private static final String UPSERT_INSERT_SQL = """
            WITH upsert_model AS (
                INSERT INTO tc_model
                    (model_name, comm_interface, maker, created_by, updated_by)
                VALUES
                    (:modelName, :commInterface, :maker, :createdBy, :updatedBy)
                ON CONFLICT (model_name)
                DO UPDATE
                       SET comm_interface = EXCLUDED.comm_interface,
                           maker = EXCLUDED.maker,
                           updated_by = EXCLUDED.updated_by,
                           updated_at = CURRENT_TIMESTAMP
                RETURNING model_key
            )
            INSERT INTO tc_model_version
                (model_key, model_version, status, description, created_at, updated_at, created_by, updated_by)
            SELECT model_key,
                   :modelVersion,
                   :status,
                   :description,
                   CURRENT_TIMESTAMP,
                   CURRENT_TIMESTAMP,
                   :createdBy,
                   :updatedBy
              FROM upsert_model
            ON CONFLICT (model_key, model_version)
            DO UPDATE
                   SET status = EXCLUDED.status,
                       description = EXCLUDED.description,
                       updated_by = EXCLUDED.updated_by,
                       updated_at = CURRENT_TIMESTAMP
            """;

    /**
     * model_version_key 기준 갱신(SQL CTE)입니다.
     */
    private static final String UPSERT_UPDATE_SQL = """
            WITH target_model AS (
                SELECT model_key
                  FROM tc_model_version
                 WHERE model_version_key = :modelVersionKey
            ),
            update_model AS (
                UPDATE tc_model
                   SET model_name = :modelName,
                       comm_interface = :commInterface,
                       maker = :maker,
                       updated_by = :updatedBy,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE model_key = (SELECT model_key FROM target_model)
                RETURNING model_key
            )
            UPDATE tc_model_version
               SET model_version = :modelVersion,
                   status = :status,
                   description = :description,
                   updated_by = :updatedBy,
                   updated_at = CURRENT_TIMESTAMP
             WHERE model_version_key = :modelVersionKey
            """;

    /**
     * model_version_key 기준 삭제 SQL입니다.
     */
    private static final String DELETE_BY_MODEL_VERSION_KEY_SQL = """
            DELETE FROM tc_model_version
             WHERE model_version_key = :modelVersionKey
            """;

    private final EntityManager entityManager;

    /**
     * 생성자 기반 DI를 통해 EntityManager를 주입받습니다.
     *
     * @param entityManager JPA EntityManager
     */
    public TcModelJpaStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * tc_model/tc_model_version 업서트를 수행합니다.
     *
     * <p>
     * 호환 규칙:
     * {@code command.modelKey > 0}이면 "model_version_key 기반 update 경로"로 처리합니다.
     * {@code command.modelKey == null 또는 0 이하}이면 "insert/upsert 경로"로 처리합니다.
     * </p>
     *
     * @param command 업서트 명령
     * @return 업서트 후 재조회된 모델
     */
    @Override
    @Transactional
    public TcModel upsert(UpsertTcModel command) {
        validateUpsertCommand(command);

        final long modelVersionKey = normalizeModelVersionKey(command.modelKey());
        final String createdBy = normalizeAuditUser(command.createdBy());
        final String updatedBy = normalizeAuditUser(command.updatedBy());

        if (log.isDebugEnabled()) {
            log.debug(
                    "tc_model JPA upsert 시작. modelName={}, modelVersion={}, commInterface={}, status={}, compatibilityModelVersionKey={}",
                    command.modelName(),
                    command.modelVersion(),
                    command.commInterface(),
                    command.status(),
                    modelVersionKey
            );
        }

        try {
            if (modelVersionKey > 0) {
                int updatedRows = entityManager.createNativeQuery(UPSERT_UPDATE_SQL)
                        .setParameter("modelVersionKey", modelVersionKey)
                        .setParameter("modelName", command.modelName())
                        .setParameter("modelVersion", command.modelVersion())
                        .setParameter("commInterface", command.commInterface().name())
                        .setParameter("status", command.status().name())
                        .setParameter("description", command.description())
                        .setParameter("maker", command.maker())
                        .setParameter("updatedBy", updatedBy)
                        .executeUpdate();

                if (updatedRows == 0) {
                    log.warn("tc_model JPA update 경로에서 수정 대상이 없습니다. modelVersionKey={}", modelVersionKey);
                } else if (log.isDebugEnabled()) {
                    log.debug("tc_model JPA update 경로 실행 완료. updatedRows={}", updatedRows);
                }
            } else {
                entityManager.createNativeQuery(UPSERT_INSERT_SQL)
                        .setParameter("modelName", command.modelName())
                        .setParameter("modelVersion", command.modelVersion())
                        .setParameter("commInterface", command.commInterface().name())
                        .setParameter("status", command.status().name())
                        .setParameter("description", command.description())
                        .setParameter("maker", command.maker())
                        .setParameter("createdBy", createdBy)
                        .setParameter("updatedBy", updatedBy)
                        .executeUpdate();

                if (log.isDebugEnabled()) {
                    log.debug("tc_model JPA insert/upsert 경로 실행 완료. modelName={}, modelVersion={}", command.modelName(), command.modelVersion());
                }
            }

            return findByNameVersionInternal(command.modelName(), command.modelVersion())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model upsert 후 재조회에 실패했습니다. nameVersion=" + command.modelName() + "/" + command.modelVersion()
                    ));

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException(
                    "tc_model upsert failed. nameVersion=" + command.modelName() + "/" + command.modelVersion(),
                    e
            );
        } catch (RuntimeException e) {
            if (isDuplicateKey(e)) {
                throw new DbDuplicateKeyException(
                        "tc_model upsert failed by duplicate key. nameVersion=" + command.modelName() + "/" + command.modelVersion(),
                        e
                );
            }
            throw new DbAccessException("tc_model upsert failed.", e);
        }
    }

    /**
     * model_version_key 기준 단건 조회를 수행합니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 조회 결과
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByModelVersionKey(long modelVersionKey) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be positive");
        }

        if (log.isDebugEnabled()) {
            log.debug("tc_model JPA findByModelVersionKey 시작. modelVersionKey={}", modelVersionKey);
        }

        try {
            Query query = entityManager.createNativeQuery(FIND_BY_MODEL_VERSION_KEY_SQL)
                    .setParameter("modelVersionKey", modelVersionKey);

            Optional<TcModel> result = mapSingleResult(query);

            if (log.isDebugEnabled()) {
                log.debug("tc_model JPA findByModelVersionKey 완료. modelVersionKey={}, found={}", modelVersionKey, result.isPresent());
            }
            return result;
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model findByModelVersionKey failed. modelVersionKey=" + modelVersionKey, e);
        }
    }

    /**
     * (model_name, model_version) 기준 단건 조회를 수행합니다.
     *
     * @param modelName 모델 이름
     * @param modelVersion 모델 버전 문자열
     * @return 조회 결과
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByNameVersion(String modelName, String modelVersion) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be null/blank");
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be null/blank");
        }

        if (log.isDebugEnabled()) {
            log.debug("tc_model JPA findByNameVersion 시작. modelName={}, modelVersion={}", modelName, modelVersion);
        }

        try {
            Optional<TcModel> result = findByNameVersionInternal(modelName, modelVersion);
            if (log.isDebugEnabled()) {
                log.debug("tc_model JPA findByNameVersion 완료. modelName={}, modelVersion={}, found={}",
                        modelName,
                        modelVersion,
                        result.isPresent());
            }
            return result;
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model findByNameVersion failed. nameVersion=" + modelName + "/" + modelVersion, e);
        }
    }

    /**
     * 모델 목록을 페이지 단위로 조회합니다.
     *
     * @param page 페이지 요청(없으면 기본값)
     * @return 조회 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModel> findAll(PageRequest page) {
        final PageRequest effectivePage = (page == null) ? PageRequest.defaultPage() : page;

        if (log.isDebugEnabled()) {
            log.debug("tc_model JPA findAll 시작. offset={}, limit={}", effectivePage.offset(), effectivePage.limit());
        }

        try {
            Query query = entityManager.createNativeQuery(FIND_ALL_SQL)
                    .setParameter("offset", effectivePage.offset())
                    .setParameter("limit", effectivePage.limit());

            @SuppressWarnings("unchecked")
            List<Object[]> rawRows = (List<Object[]>) query.getResultList();
            List<TcModel> rows = rawRows.stream()
                    .map(this::mapRow)
                    .toList();

            if (log.isDebugEnabled()) {
                log.debug("tc_model JPA findAll 완료. resultCount={}", rows.size());
            }
            return rows;
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model findAll failed.", e);
        }
    }

    /**
     * model_version_key 기준으로 tc_model_version 행을 삭제합니다.
     *
     * @param modelVersionKey 모델 버전 키
     */
    @Override
    @Transactional
    public void deleteByModelVersionKey(long modelVersionKey) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be positive");
        }

        if (log.isDebugEnabled()) {
            log.debug("tc_model JPA deleteByModelVersionKey 시작. modelVersionKey={}", modelVersionKey);
        }

        try {
            int deletedRows = entityManager.createNativeQuery(DELETE_BY_MODEL_VERSION_KEY_SQL)
                    .setParameter("modelVersionKey", modelVersionKey)
                    .executeUpdate();

            if (deletedRows == 0) {
                log.warn("tc_model JPA deleteByModelVersionKey 대상이 없습니다. modelVersionKey={}", modelVersionKey);
            } else {
                log.info("tc_model JPA deleteByModelVersionKey 완료. modelVersionKey={}, deletedRows={}", modelVersionKey, deletedRows);
            }
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model deleteByModelVersionKey failed. modelVersionKey=" + modelVersionKey, e);
        }
    }

    /**
     * 내부 공통 (model_name, model_version) 단건 조회 로직입니다.
     */
    private Optional<TcModel> findByNameVersionInternal(String modelName, String modelVersion) {
        Query query = entityManager.createNativeQuery(FIND_BY_NAME_VERSION_SQL)
                .setParameter("modelName", modelName)
                .setParameter("modelVersion", modelVersion);
        return mapSingleResult(query);
    }

    /**
     * 네이티브 단건 조회 결과를 Optional로 변환합니다.
     */
    private Optional<TcModel> mapSingleResult(Query query) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) query.getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapRow(rows.get(0)));
    }

    /**
     * SQL 행(Object[])을 TcModel 도메인으로 변환합니다.
     */
    private TcModel mapRow(Object[] row) {
        if (row.length < 12) {
            throw new DbAccessException("tc_model native query row column count is invalid: " + row.length);
        }

        return new TcModel(
                toLong(row[0], "model_version_key"),
                toLong(row[1], "model_key"),
                toRequiredString(row[2], "model_name"),
                toRequiredString(row[3], "model_version"),
                toProtocolType(row[4]),
                toModelStatus(row[5]),
                toOptionalString(row[6]),
                toOptionalString(row[7]),
                toOffsetDateTime(row[8], "created_at"),
                toOffsetDateTime(row[9], "updated_at"),
                toRequiredString(row[10], "created_by"),
                toRequiredString(row[11], "updated_by")
        );
    }

    /**
     * 숫자형 DB 값을 long으로 변환합니다.
     */
    private long toLong(Object value, String columnName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        throw new IllegalArgumentException("column " + columnName + " cannot be converted to long. value=" + value);
    }

    /**
     * 필수 문자열 컬럼을 변환합니다.
     */
    private String toRequiredString(Object value, String columnName) {
        String text = toOptionalString(value);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("column " + columnName + " must not be null/blank");
        }
        return text;
    }

    /**
     * 선택 문자열 컬럼을 변환합니다.
     */
    private String toOptionalString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * DB 문자열을 ProtocolType enum으로 변환합니다.
     */
    private ProtocolType toProtocolType(Object value) {
        String text = toRequiredString(value, "comm_interface");
        return ProtocolType.valueOf(text.toUpperCase(Locale.ROOT));
    }

    /**
     * DB 문자열을 ModelStatus enum으로 변환합니다.
     */
    private ModelStatus toModelStatus(Object value) {
        String text = toRequiredString(value, "status");
        return ModelStatus.valueOf(text.toUpperCase(Locale.ROOT));
    }

    /**
     * DB 시간 값을 OffsetDateTime으로 변환합니다.
     */
    private OffsetDateTime toOffsetDateTime(Object value, String columnName) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof String text && !text.isBlank()) {
            return OffsetDateTime.parse(text);
        }
        throw new IllegalArgumentException("column " + columnName + " cannot be converted to OffsetDateTime. value=" + value);
    }

    /**
     * 업서트 입력의 필수값을 검증합니다.
     */
    private void validateUpsertCommand(UpsertTcModel command) {
        if (command == null) {
            throw new IllegalArgumentException("UpsertTcModel must not be null");
        }
        if (command.modelName() == null || command.modelName().isBlank()) {
            throw new IllegalArgumentException("modelName must not be null/blank");
        }
        if (command.modelVersion() == null || command.modelVersion().isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be null/blank");
        }
        if (command.commInterface() == null) {
            throw new IllegalArgumentException("commInterface must not be null");
        }
        if (command.status() == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (command.modelKey() != null && command.modelKey() < 0) {
            throw new IllegalArgumentException("modelKey compatibility value must be >= 0");
        }
    }

    /**
     * 호환 입력값(modelKey)을 modelVersionKey로 정규화합니다.
     */
    private long normalizeModelVersionKey(Long compatibilityModelKey) {
        if (compatibilityModelKey == null) {
            return 0L;
        }
        return Math.max(compatibilityModelKey, 0L);
    }

    /**
     * 감사 사용자 값을 정규화합니다.
     */
    private String normalizeAuditUser(String auditUser) {
        if (auditUser == null || auditUser.isBlank()) {
            return "SYSTEM";
        }
        return auditUser;
    }

    /**
     * RuntimeException 원인 체인을 탐색해서 중복키 상황인지 판단합니다.
     */
    private boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                if ("23505".equals(sqlException.getSQLState())) {
                    return true;
                }
            }

            String className = current.getClass().getSimpleName();
            if (className.contains("ConstraintViolationException") || className.contains("Duplicate")) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("duplicate key")
                        || lower.contains("unique constraint")
                        || lower.contains("uk_tc_model")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }
}
