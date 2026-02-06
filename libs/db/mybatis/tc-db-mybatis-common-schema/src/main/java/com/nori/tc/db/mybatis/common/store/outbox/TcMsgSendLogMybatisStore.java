package com.nori.tc.db.mybatis.common.store.outbox;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.outbox.store.TcMsgSendLogStore;
import com.nori.tc.db.core.outbox.upsert.UpsertTcMsgSendLog;
import com.nori.tc.db.domain.outbox.TcMsgSendLog;
import com.nori.tc.db.mybatis.common.mapper.outbox.TcMsgSendLogMapper;

/**
 * tc_msg_send_log MyBatis Store 구현체.
 *
 * - 논리 키: (msg_key, attempt_no)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 */
@Repository
public class TcMsgSendLogMybatisStore implements TcMsgSendLogStore {

    private final TcMsgSendLogMapper mapper;

    public TcMsgSendLogMybatisStore(TcMsgSendLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcMsgSendLog upsert(UpsertTcMsgSendLog command) {
        UpsertTcMsgSendLog normalized = normalizeCommand(command);
        validateCommand(normalized);

        final long msgKey = normalized.msgKey();
        final int attemptNo = normalized.attemptNo();

        final TcMsgSendLog row = new TcMsgSendLog(
                0L,
                msgKey,
                attemptNo,
                normalized.result(),
                normalized.kafkaPartition(),
                normalized.kafkaOffset(),
                normalized.errorCode(),
                normalized.errorMessage(),
                normalized.sentAt()
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

            return mapper.findByMsgKeyAttemptNo(msgKey, attemptNo)
                    .orElseThrow(() -> new DbAccessException("tc_msg_send_log upsert succeeded but row not found. msgKey/attemptNo=" + msgKey + "/" + attemptNo));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_msg_send_log upsert duplicate key. msgKey/attemptNo=" + msgKey + "/" + attemptNo, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_msg_send_log upsert failed. msgKey/attemptNo=" + msgKey + "/" + attemptNo, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_msg_send_log upsert failed (unexpected). msgKey/attemptNo=" + msgKey + "/" + attemptNo, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcMsgSendLog> findByMsgKeyAttemptNo(long msgKey, int attemptNo) {
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be positive");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be >= 1");
        }
        try {
            return mapper.findByMsgKeyAttemptNo(msgKey, attemptNo);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_msg_send_log findByMsgKeyAttemptNo failed. msgKey/attemptNo=" + msgKey + "/" + attemptNo, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_msg_send_log findByMsgKeyAttemptNo failed (unexpected). msgKey/attemptNo=" + msgKey + "/" + attemptNo, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcMsgSendLog> findAllByMsgKey(long msgKey, PageRequest page) {
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be positive");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByMsgKey(msgKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_msg_send_log findAllByMsgKey failed. msgKey=" + msgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_msg_send_log findAllByMsgKey failed (unexpected). msgKey=" + msgKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByMsgKeyAttemptNo(long msgKey, int attemptNo) {
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be positive");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be >= 1");
        }
        try {
            mapper.deleteByMsgKeyAttemptNo(msgKey, attemptNo);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_msg_send_log deleteByMsgKeyAttemptNo failed. msgKey/attemptNo=" + msgKey + "/" + attemptNo, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_msg_send_log deleteByMsgKeyAttemptNo failed (unexpected). msgKey/attemptNo=" + msgKey + "/" + attemptNo, e);
        }
    }

    private void validateCommand(UpsertTcMsgSendLog command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.msgKey() == null || command.msgKey() <= 0) {
            throw new IllegalArgumentException("command.msgKey must be positive");
        }
        if (command.attemptNo() == null || command.attemptNo() < 1) {
            throw new IllegalArgumentException("command.attemptNo must be >= 1");
        }
        if (command.result() == null) {
            throw new IllegalArgumentException("command.result must not be null");
        }
    }

    private UpsertTcMsgSendLog normalizeCommand(UpsertTcMsgSendLog command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcMsgSendLog(
                command.msgKey(),
                command.attemptNo(),
                command.result(),
                command.kafkaPartition(),
                command.kafkaOffset(),
                command.errorCode(),
                command.errorMessage(),
                command.sentAt() == null ? OffsetDateTime.now() : command.sentAt()
        );
    }
}
