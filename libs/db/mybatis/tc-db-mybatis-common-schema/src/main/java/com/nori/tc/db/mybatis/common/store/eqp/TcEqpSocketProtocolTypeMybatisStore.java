package com.nori.tc.db.mybatis.common.store.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpSocketProtocolTypeStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocketProtocolType;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpSocketProtocolTypeMapper;

/**
 * tc_eqp_socket_protocol_type MyBatis Store 구현체.
 */
@Repository
public class TcEqpSocketProtocolTypeMybatisStore implements TcEqpSocketProtocolTypeStore {

    private final TcEqpSocketProtocolTypeMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcEqpSocketProtocolTypeMybatisStore(TcEqpSocketProtocolTypeMapper mapper) {
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
    public TcEqpSocketProtocolType upsert(UpsertTcEqpSocketProtocolType command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        final TcEqpSocketProtocolType row = new TcEqpSocketProtocolType(
                command.socketProtocolType(),
                command.socketProtocolTypeName(),
                command.parseStartRule(),
                command.parseEndRule(),
                command.parseRegex(),
                command.description()
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

            return mapper.findBySocketProtocolType(command.socketProtocolType())
                    .orElseThrow(() -> new DbAccessException("tc_eqp_socket_protocol_type upsert succeeded but row not found. socketProtocolType=" + command.socketProtocolType()));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_socket_protocol_type upsert duplicate key. socketProtocolType=" + command.socketProtocolType(), e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type upsert failed. socketProtocolType=" + command.socketProtocolType(), e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type upsert failed (unexpected). socketProtocolType=" + command.socketProtocolType(), e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpSocketProtocolType> findBySocketProtocolType(String socketProtocolType) {
        try {
            return mapper.findBySocketProtocolType(socketProtocolType);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type findBySocketProtocolType failed. socketProtocolType=" + socketProtocolType, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type findBySocketProtocolType failed (unexpected). socketProtocolType=" + socketProtocolType, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcEqpSocketProtocolType> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAll(p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type findAll failed", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type findAll failed (unexpected)", e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     */
    @Override
    @Transactional
    public void deleteBySocketProtocolType(String socketProtocolType) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteBySocketProtocolType(socketProtocolType);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type deleteBySocketProtocolType failed. socketProtocolType=" + socketProtocolType, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type deleteBySocketProtocolType failed (unexpected). socketProtocolType=" + socketProtocolType, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpSocketProtocolType command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.socketProtocolType() == null || command.socketProtocolType().isBlank()) {
            throw new IllegalArgumentException("command.socketProtocolType must not be null/blank");
        }
        if (command.socketProtocolTypeName() == null || command.socketProtocolTypeName().isBlank()) {
            throw new IllegalArgumentException("command.socketProtocolTypeName must not be null/blank");
        }
    }
}
