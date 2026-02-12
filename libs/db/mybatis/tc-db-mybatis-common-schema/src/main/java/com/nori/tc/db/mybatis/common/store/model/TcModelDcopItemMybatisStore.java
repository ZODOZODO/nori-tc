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
import com.nori.tc.db.core.model.store.TcModelDcopItemStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelDcopItem;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelDcopItemMapper;

/**
 * tc_model_dcop_item MyBatis Store 구현체.
 */
@Repository
public class TcModelDcopItemMybatisStore implements TcModelDcopItemStore {

    private final TcModelDcopItemMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelDcopItemMybatisStore(TcModelDcopItemMapper mapper) {
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
    public TcModelDcopItem upsert(UpsertTcModelDcopItem command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateUpsert(command);

        // [FIX] Upsert 커맨드와 도메인 모델 필드 순서를 정확히 매핑한다.
        // - 기존 구현은 존재하지 않는 dcopItemDesc/createdAt/updatedAt를 참조해 컴파일 오류가 발생했다.
        final TcModelDcopItem row = new TcModelDcopItem(
                null,
                command.modelKey(),
                command.dcopItemName(),
                command.workflowName(),
                command.eventId(),
                command.variableId(),
                command.collectionRule(),
                command.calculationRule(),
                command.orderRule(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                mapper.insert(row);
            }
            return mapper.findByModelKeyAndName(command.modelKey(), command.dcopItemName())
                    .orElseThrow(() -> new DbAccessException("Upsert success but find failed"));
        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_dcop_item duplicate key", e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_dcop_item upsert failed", e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param dcopItemName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelDcopItem> findByModelKeyAndName(long modelKey, String dcopItemName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (dcopItemName == null || dcopItemName.isBlank()) {
            throw new IllegalArgumentException("dcopItemName must not be null/blank");
        }
        try {
            return mapper.findByModelKeyAndName(modelKey, dcopItemName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_dcop_item findByModelKeyAndName failed", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_dcop_item findByModelKeyAndName failed (unexpected)", e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelDcopItem> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_dcop_item findAllByModelKey failed", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_dcop_item findAllByModelKey failed (unexpected)", e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param dcopItemName DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByModelKeyAndName(long modelKey, String dcopItemName) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (dcopItemName == null || dcopItemName.isBlank()) {
            throw new IllegalArgumentException("dcopItemName must not be null/blank");
        }
        try {
            mapper.deleteByModelKeyAndName(modelKey, dcopItemName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_dcop_item deleteByModelKeyAndName failed", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_dcop_item deleteByModelKeyAndName failed (unexpected)", e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateUpsert(UpsertTcModelDcopItem command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.dcopItemName() == null || command.dcopItemName().isBlank()) {
            throw new IllegalArgumentException("command.dcopItemName must not be null/blank");
        }
    }
}
