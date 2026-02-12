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
import com.nori.tc.db.core.model.store.TcModelSocketMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelSocketMessageMapper;

/**
 * tc_model_socket_message MyBatis Store 구현체.
 *
 * - Unique: (model_key, socket_msg_name)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 */
@Repository
public class TcModelSocketMessageMybatisStore implements TcModelSocketMessageStore {

    private final TcModelSocketMessageMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelSocketMessageMybatisStore(TcModelSocketMessageMapper mapper) {
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
    public TcModelSocketMessage upsert(UpsertTcModelSocketMessage command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        final long modelKey = command.modelKey();
        final String socketMsgName = command.socketMsgName();

        final TcModelSocketMessage row = new TcModelSocketMessage(
                0L,
                modelKey,
                socketMsgName,
                command.description(),
                command.dataIndex(),
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

            return mapper.findByModelKeySocketMsgName(modelKey, socketMsgName)
                    .orElseThrow(() -> new DbAccessException("tc_model_socket_message upsert succeeded but row not found. modelKey/socketMsgName=" + modelKey + "/" + socketMsgName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_socket_message upsert duplicate key. modelKey/socketMsgName=" + modelKey + "/" + socketMsgName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_socket_message upsert failed. modelKey/socketMsgName=" + modelKey + "/" + socketMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_socket_message upsert failed (unexpected). modelKey/socketMsgName=" + modelKey + "/" + socketMsgName, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param socketMsgName 통신 채널/세션 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSocketMessage> findByModelKeySocketMsgName(long modelKey, String socketMsgName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (socketMsgName == null || socketMsgName.isBlank()) {
            throw new IllegalArgumentException("socketMsgName must not be null/blank");
        }
        try {
            return mapper.findByModelKeySocketMsgName(modelKey, socketMsgName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_socket_message findByModelKeySocketMsgName failed. modelKey/socketMsgName=" + modelKey + "/" + socketMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_socket_message findByModelKeySocketMsgName failed (unexpected). modelKey/socketMsgName=" + modelKey + "/" + socketMsgName, e);
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
    public List<TcModelSocketMessage> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_socket_message findAllByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_socket_message findAllByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param socketMsgName 통신 채널/세션 정보
     */
    @Override
    @Transactional
    public void deleteByModelKeySocketMsgName(long modelKey, String socketMsgName) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (socketMsgName == null || socketMsgName.isBlank()) {
            throw new IllegalArgumentException("socketMsgName must not be null/blank");
        }
        try {
            mapper.deleteByModelKeySocketMsgName(modelKey, socketMsgName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_socket_message deleteByModelKeySocketMsgName failed. modelKey/socketMsgName=" + modelKey + "/" + socketMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_socket_message deleteByModelKeySocketMsgName failed (unexpected). modelKey/socketMsgName=" + modelKey + "/" + socketMsgName, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcModelSocketMessage command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.socketMsgName() == null || command.socketMsgName().isBlank()) {
            throw new IllegalArgumentException("command.socketMsgName must not be null/blank");
        }
    }
}
