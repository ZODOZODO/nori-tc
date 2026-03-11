package com.nori.tc.db.mybatis.common.store.model;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelReportIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelReportId;
import com.nori.tc.db.domain.model.TcModelReportId;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelReportIdMapper;

/**
 * tc_model_reportid MyBatis Store 구현체.
 */
@Repository
public class TcModelReportIdMybatisStore implements TcModelReportIdStore {

    private final TcModelReportIdMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelReportIdMybatisStore(TcModelReportIdMapper mapper) {
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
    public TcModelReportId upsert(UpsertTcModelReportId command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateUpsert(command);

        final long modelVersionKey = command.modelVersionKey();
        final String reportId = command.reportId();

        // [FIX] 도메인 모델 시그니처에 맞춰 variableId/updatedAt만 채운다.
        // - 기존 구현은 reportName/createdAt/updatedAt 필드를 참조해 컴파일 오류가 발생했다.
        final TcModelReportId row = new TcModelReportId(
                0L,
                modelVersionKey,
                reportId,
                command.variableId(),
                command.enabled(),
                command.description(),
                null
        );

        try {
            int updated = mapper.updateByUniqueKey(row);
            if (updated == 0) {
                mapper.insert(row);
            }
            return mapper.findByModelVersionKeyAndReportId(modelVersionKey, reportId)
                    .orElseThrow(() -> new DbAccessException("tc_model_reportid upsert succeeded but row not found. modelVersionKey/reportId=" + modelVersionKey + "/" + reportId));
        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_reportid duplicate key. modelVersionKey/reportId=" + modelVersionKey + "/" + reportId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid upsert failed. modelVersionKey/reportId=" + modelVersionKey + "/" + reportId, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param reportKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelReportId> findByReportKey(long reportKey) {
        try {
            return mapper.findByReportKey(reportKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid findByReportKey failed. reportKey=" + reportKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_reportid findByReportKey failed (unexpected). reportKey=" + reportKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param reportId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelReportId> findByModelVersionKeyAndReportId(long modelVersionKey, String reportId) {
        try {
            return mapper.findByModelVersionKeyAndReportId(modelVersionKey, reportId);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_reportid findByModelVersionKeyAndReportId failed. modelVersionKey=" + modelVersionKey + ", reportId=" + reportId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_reportid findByModelVersionKeyAndReportId failed (unexpected). modelVersionKey=" + modelVersionKey + ", reportId=" + reportId,
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelReportId> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelVersionKey(
                    modelVersionKey,
                    p.offset(),
                    p.limit()
            );
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid findAllByModelVersionKey failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_reportid findAllByModelVersionKey failed (unexpected).", e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param reportKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByReportKey(long reportKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByReportKey(reportKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid deleteByReportKey failed. reportKey=" + reportKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_reportid deleteByReportKey failed (unexpected). reportKey=" + reportKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateUpsert(UpsertTcModelReportId command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelVersionKey() <= 0) throw new IllegalArgumentException("command.modelVersionKey must be > 0");
        if (command.reportId() == null || command.reportId().isBlank()) {
            throw new IllegalArgumentException("command.reportId must not be null/blank");
        }
    }
}
