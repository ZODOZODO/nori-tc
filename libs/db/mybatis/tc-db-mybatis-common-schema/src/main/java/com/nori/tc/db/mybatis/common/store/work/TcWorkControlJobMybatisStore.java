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
import com.nori.tc.db.core.work.store.TcWorkControlJobStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkControlJob;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkControlJobMapper;

/**
 * tc_work_controljob MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (work_key, controljob_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcWorkControlJobMybatisStore implements TcWorkControlJobStore {

    private final TcWorkControlJobMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcWorkControlJobMybatisStore(TcWorkControlJobMapper mapper) {
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
    public TcWorkControlJob upsert(UpsertTcWorkControlJob command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        final long resolvedKey = resolveKey(command);

        final TcWorkControlJob row = new TcWorkControlJob(
                resolvedKey,
                command.workKey(),
                command.controljobId(),
                command.controljobState(),
                null,
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_work_controljob insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByWorkKeyAndControljobId(command.workKey(), command.controljobId())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_work_controljob upsert succeeded but row not found. key=" + command.workKey() + "/" + command.controljobId()
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_work_controljob upsert duplicate (work_key, controljob_id). key=" + command.workKey() + "/" + command.controljobId(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_controljob upsert failed. key=" + command.workKey() + "/" + command.controljobId(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_controljob upsert failed (unexpected). key=" + command.workKey() + "/" + command.controljobId(),
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkControlJob> findByControlJobKey(long controlJobKey) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        try {
            return mapper.findByControlJobKey(controlJobKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_controljob findByControlJobKey failed. controlJobKey=" + controlJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_controljob findByControlJobKey failed (unexpected). controlJobKey=" + controlJobKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param controljobId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkControlJob> findByWorkKeyAndControljobId(long workKey, String controljobId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (controljobId == null || controljobId.isBlank()) {
            throw new IllegalArgumentException("controljobId must not be null/blank");
        }
        try {
            return mapper.findByWorkKeyAndControljobId(workKey, controljobId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_controljob findByWorkKeyAndControljobId failed. key=" + workKey + "/" + controljobId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_controljob findByWorkKeyAndControljobId failed (unexpected). key=" + workKey + "/" + controljobId, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcWorkControlJob> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByWorkKey(workKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_controljob findAllByWorkKey failed. workKey=" + workKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_controljob findAllByWorkKey failed (unexpected). workKey=" + workKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByControlJobKey(long controlJobKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        try {
            mapper.deleteByControlJobKey(controlJobKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_controljob deleteByControlJobKey failed. controlJobKey=" + controlJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_controljob deleteByControlJobKey failed (unexpected). controlJobKey=" + controlJobKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcWorkControlJob command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.controlJobKey() != null && command.controlJobKey() <= 0) {
            throw new IllegalArgumentException("command.controlJobKey must be > 0 when provided");
        }
        if (command.workKey() <= 0) {
            throw new IllegalArgumentException("command.workKey must be > 0");
        }
        if (command.controljobId() == null || command.controljobId().isBlank()) {
            throw new IllegalArgumentException("command.controljobId must not be null/blank");
        }
        if (command.controljobState() == null) {
            throw new IllegalArgumentException("command.controljobState must not be null");
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private long resolveKey(UpsertTcWorkControlJob command) {
        if (command.controlJobKey() != null) {
            return command.controlJobKey();
        }

        return mapper.findByWorkKeyAndControljobId(command.workKey(), command.controljobId())
                .map(TcWorkControlJob::controlJobKey)
                .orElse(0L);
    }
}
