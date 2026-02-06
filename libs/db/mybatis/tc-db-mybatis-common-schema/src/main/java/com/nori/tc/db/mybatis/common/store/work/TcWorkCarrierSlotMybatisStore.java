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

    public TcWorkCarrierSlotMybatisStore(TcWorkCarrierSlotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkCarrierSlot upsert(UpsertTcWorkCarrierSlot command) {
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

    @Override
    @Transactional
    public void deleteByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo) {
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

    private void validateCommand(UpsertTcWorkCarrierSlot command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workCarrierKey() <= 0) throw new IllegalArgumentException("command.workCarrierKey must be > 0");
        if (command.slotNo() < 1) throw new IllegalArgumentException("command.slotNo must be >= 1");
        if (command.slotState() == null || command.slotState().isBlank()) {
            throw new IllegalArgumentException("command.slotState must not be null/blank");
        }
    }
}
