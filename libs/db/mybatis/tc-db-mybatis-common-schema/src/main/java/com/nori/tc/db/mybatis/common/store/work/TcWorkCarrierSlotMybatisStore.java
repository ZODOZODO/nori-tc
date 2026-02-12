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
import com.nori.tc.db.core.work.store.TcWorkCarrierSlotStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrierSlot;
import com.nori.tc.db.domain.work.TcWorkCarrierSlot;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkCarrierSlotMapper;

/**
 * tc_work_carrier_slot MyBatis Store 구현체.
 *
 * - Unique: (work_carrier_key, slot_no)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 */
@Repository
public class TcWorkCarrierSlotMybatisStore implements TcWorkCarrierSlotStore {

    private final TcWorkCarrierSlotMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcWorkCarrierSlotMybatisStore(TcWorkCarrierSlotMapper mapper) {
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
    public TcWorkCarrierSlot upsert(UpsertTcWorkCarrierSlot command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        final long workCarrierKey = command.workCarrierKey();
        final int slotNo = command.slotNo();

        final TcWorkCarrierSlot row = new TcWorkCarrierSlot(
                0L,
                workCarrierKey,
                slotNo,
                command.slotState(),
                command.lotId(),
                command.updatedAt() // SQL에서는 CURRENT_TIMESTAMP로 갱신(입력값은 참고용)
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.update(row);
                }
            }

            return mapper.findByWorkCarrierKeySlotNo(workCarrierKey, slotNo)
                    .orElseThrow(() -> new DbAccessException("tc_work_carrier_slot upsert succeeded but row not found. workCarrierKey/slotNo=" + workCarrierKey + "/" + slotNo));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_work_carrier_slot upsert duplicate key. workCarrierKey/slotNo=" + workCarrierKey + "/" + slotNo, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier_slot upsert failed. workCarrierKey/slotNo=" + workCarrierKey + "/" + slotNo, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier_slot upsert failed (unexpected). workCarrierKey/slotNo=" + workCarrierKey + "/" + slotNo, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param slotNo DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkCarrierSlot> findByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo) {
        if (workCarrierKey <= 0) {
            throw new IllegalArgumentException("workCarrierKey must be > 0");
        }
        if (slotNo < 1) {
            throw new IllegalArgumentException("slotNo must be >= 1");
        }
        try {
            return mapper.findByWorkCarrierKeySlotNo(workCarrierKey, slotNo);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier_slot findByWorkCarrierKeySlotNo failed. workCarrierKey/slotNo=" + workCarrierKey + "/" + slotNo, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier_slot findByWorkCarrierKeySlotNo failed (unexpected). workCarrierKey/slotNo=" + workCarrierKey + "/" + slotNo, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcWorkCarrierSlot> findAllByWorkCarrierKey(long workCarrierKey, PageRequest page) {
        if (workCarrierKey <= 0) {
            throw new IllegalArgumentException("workCarrierKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByWorkCarrierKey(workCarrierKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier_slot findAllByWorkCarrierKey failed. workCarrierKey=" + workCarrierKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier_slot findAllByWorkCarrierKey failed (unexpected). workCarrierKey=" + workCarrierKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param slotNo DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (workCarrierKey <= 0) {
            throw new IllegalArgumentException("workCarrierKey must be > 0");
        }
        if (slotNo < 1) {
            throw new IllegalArgumentException("slotNo must be >= 1");
        }
        try {
            mapper.deleteByWorkCarrierKeySlotNo(workCarrierKey, slotNo);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier_slot deleteByWorkCarrierKeySlotNo failed. workCarrierKey/slotNo=" + workCarrierKey + "/" + slotNo, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier_slot deleteByWorkCarrierKeySlotNo failed (unexpected). workCarrierKey/slotNo=" + workCarrierKey + "/" + slotNo, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcWorkCarrierSlot command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workCarrierKey() <= 0) throw new IllegalArgumentException("command.workCarrierKey must be > 0");
        if (command.slotNo() < 1) throw new IllegalArgumentException("command.slotNo must be >= 1");
        if (command.slotState() == null || command.slotState().isBlank()) {
            throw new IllegalArgumentException("command.slotState must not be null/blank");
        }
    }
}
