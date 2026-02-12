package com.nori.tc.db.mybatis.common.store.work;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.work.store.TcWorkProcessjobLotMapStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessjobLotMap;
import com.nori.tc.db.domain.work.TcWorkProcessjobLotMap;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkProcessjobLotMapMapper;

/**
 * tc_work_processjob_lot_map MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (process_job_key, work_lot_key)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcWorkProcessjobLotMapMybatisStore implements TcWorkProcessjobLotMapStore {

    private final TcWorkProcessjobLotMapMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcWorkProcessjobLotMapMybatisStore(TcWorkProcessjobLotMapMapper mapper) {
        this.mapper = mapper;
    }

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    @Transactional
    public TcWorkProcessjobLotMap upsert(UpsertTcWorkProcessjobLotMap command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        final long resolvedKey = resolveKey(command);

        final TcWorkProcessjobLotMap row = new TcWorkProcessjobLotMap(
                resolvedKey,
                command.processJobKey(),
                command.workLotKey(),
                command.mapRole(),
                command.mapOrder(),
                null,
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_work_processjob_lot_map insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByProcessJobKeyAndWorkLotKey(command.processJobKey(), command.workLotKey())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_work_processjob_lot_map upsert succeeded but row not found. key="
                                    + command.processJobKey() + "/" + command.workLotKey()
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_work_processjob_lot_map upsert duplicate (process_job_key, work_lot_key). key="
                            + command.processJobKey() + "/" + command.workLotKey(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_processjob_lot_map upsert failed. key="
                            + command.processJobKey() + "/" + command.workLotKey(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_processjob_lot_map upsert failed (unexpected). key="
                            + command.processJobKey() + "/" + command.workLotKey(),
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param pjLotMapKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessjobLotMap> findByPjLotMapKey(long pjLotMapKey) {
        if (pjLotMapKey <= 0) {
            throw new IllegalArgumentException("pjLotMapKey must be > 0");
        }
        try {
            return mapper.findByPjLotMapKey(pjLotMapKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob_lot_map findByPjLotMapKey failed. pjLotMapKey=" + pjLotMapKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob_lot_map findByPjLotMapKey failed (unexpected). pjLotMapKey=" + pjLotMapKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     * @param workLotKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessjobLotMap> findByProcessJobKeyAndWorkLotKey(long processJobKey, long workLotKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        if (workLotKey <= 0) {
            throw new IllegalArgumentException("workLotKey must be > 0");
        }
        try {
            return mapper.findByProcessJobKeyAndWorkLotKey(processJobKey, workLotKey);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_processjob_lot_map findByUniqueKey failed. key=" + processJobKey + "/" + workLotKey,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_processjob_lot_map findByUniqueKey failed (unexpected). key=" + processJobKey + "/" + workLotKey,
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcWorkProcessjobLotMap> findAllByProcessJobKey(long processJobKey, PageRequest page) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByProcessJobKey(processJobKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob_lot_map findAllByProcessJobKey failed. processJobKey=" + processJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob_lot_map findAllByProcessJobKey failed (unexpected). processJobKey=" + processJobKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param pjLotMapKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByPjLotMapKey(long pjLotMapKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (pjLotMapKey <= 0) {
            throw new IllegalArgumentException("pjLotMapKey must be > 0");
        }
        try {
            mapper.deleteByPjLotMapKey(pjLotMapKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob_lot_map deleteByPjLotMapKey failed. pjLotMapKey=" + pjLotMapKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob_lot_map deleteByPjLotMapKey failed (unexpected). pjLotMapKey=" + pjLotMapKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcWorkProcessjobLotMap command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.pjLotMapKey() != null && command.pjLotMapKey() <= 0) {
            throw new IllegalArgumentException("command.pjLotMapKey must be > 0 when provided");
        }
        if (command.processJobKey() <= 0) {
            throw new IllegalArgumentException("command.processJobKey must be > 0");
        }
        if (command.workLotKey() <= 0) {
            throw new IllegalArgumentException("command.workLotKey must be > 0");
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private long resolveKey(UpsertTcWorkProcessjobLotMap command) {
        if (command.pjLotMapKey() != null) {
            return command.pjLotMapKey();
        }

        return mapper.findByProcessJobKeyAndWorkLotKey(command.processJobKey(), command.workLotKey())
                .map(TcWorkProcessjobLotMap::pjLotMapKey)
                .orElse(0L);
    }
}
