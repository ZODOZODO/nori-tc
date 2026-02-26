package com.nori.tc.db.mybatis.common.store.eqp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocket;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpMapper;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpSocketMapper;

/**
 * tc_eqp_socket MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_key)
 * - charset 기본값('UTF-8')은 SQL에서 COALESCE로 안전 처리됨
 */
@Repository
public class TcEqpSocketMybatisStore implements TcEqpSocketStore {

    private static final Logger log = LoggerFactory.getLogger(TcEqpSocketMybatisStore.class);
    private static final String PASSIVE_MODE = "PASSIVE";
    private static final int VALIDATION_SCAN_PAGE_SIZE = 500;

    private final TcEqpSocketMapper mapper;
    private final TcEqpMapper eqpMapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     * @param eqpMapper parent tc_eqp 조회/검증에 사용하는 MyBatis 매퍼
     */
    public TcEqpSocketMybatisStore(TcEqpSocketMapper mapper, TcEqpMapper eqpMapper) {
        this.mapper = mapper;
        this.eqpMapper = eqpMapper;
        log.info("tc_eqp_socket MyBatis Store 초기화 완료. PASSIVE listener-group route_partition 검증이 활성화됩니다.");
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
    public TcEqpSocket upsert(UpsertTcEqpSocket command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final long eqpKey = command.eqpKey();
        validatePassiveSocketListenerGroupRoutePartition(command);

        final TcEqpSocket row = new TcEqpSocket(
                eqpKey,
                command.socketProtocolType(),
                command.charset(),
                command.heartbeatEnabled(),
                command.heartbeatInterval(),
                command.readTimeout(),
                command.writeTimeout(),
                command.maxFrameSizeBytes(),
                command.keepAliveEnabled(),
                command.createdAt(),
                command.updatedAt()
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

            return mapper.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_socket upsert succeeded but row not found. eqpKey=" + eqpKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_socket upsert duplicate key. eqpKey=" + eqpKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket upsert failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket upsert failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    /**
     * PASSIVE SOCKET 설비의 listener-group(route key) 기준 route_partition 동일성 규칙을 검증합니다.
     *
     * <p>검증 목적:</p>
     * <p>- 동일 listener-group(interface=SOCKET + eqp_ip + eqp_port + socket_protocol_type)을 공유하는 설비들이
     *   서로 다른 {@code route_partition}으로 저장되는 것을 사전에 차단합니다.</p>
     *
     * <p>검증 시점:</p>
     * <p>- {@link #upsert(UpsertTcEqpSocket)} 저장 직전</p>
     *
     * <p>검증 범위:</p>
     * <p>- parent {@code tc_eqp.comm_mode}가 PASSIVE인 경우에만 적용</p>
     * <p>- ACTIVE 설비는 listener-group 공유 개념 대상이 아니므로 검증을 건너뜁니다.</p>
     *
     * @param command SOCKET 상세 설정 upsert 요청
     */
    private void validatePassiveSocketListenerGroupRoutePartition(final UpsertTcEqpSocket command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        try {
            // 1) parent tc_eqp를 조회하여 공통 통신 모드/주소/route_partition을 확인합니다.
            final TcEqp parent = loadRequiredParentEqp(command.eqpKey());

            if (!PASSIVE_MODE.equals(parent.commMode())) {
                if (log.isDebugEnabled()) {
                    log.debug("PASSIVE listener-group route_partition 검증을 건너뜁니다. 사유=ACTIVE 설비, eqpId={}, eqpKey={}, commMode={}",
                            parent.eqpId(),
                            parent.eqpKey(),
                            parent.commMode());
                }
                return;
            }

            if (parent.commInterface() != ProtocolType.SOCKET) {
                throw new IllegalStateException("tc_eqp_socket upsert 대상의 parent comm_interface가 SOCKET이 아닙니다. eqpKey="
                        + command.eqpKey() + ", commInterface=" + parent.commInterface());
            }

            if (log.isDebugEnabled()) {
                log.debug("PASSIVE SOCKET listener-group route_partition 검증 시작. eqpId={}, eqpKey={}, routePartition={}, bind={}:{}, socketProtocolType={}",
                        parent.eqpId(),
                        parent.eqpKey(),
                        parent.routePartition(),
                        parent.eqpIp(),
                        parent.eqpPort(),
                        command.socketProtocolType());
            }

            // 2) 동일 listener-group 후보를 찾기 위해 tc_eqp 전체를 페이징 스캔합니다.
            //    등록/수정 경로는 빈도가 낮으므로 가독성과 명확성을 우선합니다.
            int candidateCount = 0;
            for (TcEqp peer : loadAllEqpRowsForValidation()) {
                if (!isPassiveSocketBaseCandidate(parent, peer)) {
                    continue;
                }
                if (Objects.equals(parent.eqpKey(), peer.eqpKey())) {
                    continue;
                }

                final long peerEqpKey = peer.eqpKey();
                final TcEqpSocket peerSocket = mapper.findByEqpKey(peerEqpKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "PASSIVE SOCKET route_partition 검증 중 peer tc_eqp_socket 행이 없습니다. peerEqpKey=" + peerEqpKey));

                if (!sameText(command.socketProtocolType(), peerSocket.socketProtocolType())) {
                    continue;
                }

                candidateCount++;

                if (!Objects.equals(parent.routePartition(), peer.routePartition())) {
                    log.warn("PASSIVE SOCKET listener-group route_partition 불일치 감지. currentEqpId={}, currentEqpKey={}, currentRoutePartition={}, "
                                    + "peerEqpId={}, peerEqpKey={}, peerRoutePartition={}, bind={}:{}, socketProtocolType={}",
                            parent.eqpId(),
                            parent.eqpKey(),
                            parent.routePartition(),
                            peer.eqpId(),
                            peer.eqpKey(),
                            peer.routePartition(),
                            parent.eqpIp(),
                            parent.eqpPort(),
                            command.socketProtocolType());
                    throw new IllegalArgumentException("동일 PASSIVE SOCKET listener-group의 route_partition은 동일해야 합니다. "
                            + "currentEqpId=" + parent.eqpId() + ", peerEqpId=" + peer.eqpId());
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("PASSIVE SOCKET listener-group route_partition 검증 완료. eqpId={}, eqpKey={}, 후보수={}",
                        parent.eqpId(),
                        parent.eqpKey(),
                        candidateCount);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket PASSIVE listener-group route_partition 검증 실패(DB). eqpKey=" + command.eqpKey(), e);
        } catch (DbAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket PASSIVE listener-group route_partition 검증 실패. eqpKey=" + command.eqpKey(), e);
        }
    }

    /**
     * 검증 대상 parent(tc_eqp) 행을 {@code eqp_key}로 조회합니다.
     *
     * <p>SOCKET 상세 설정 저장은 parent(tc_eqp) 선행 저장이 전제이므로, parent가 없으면 즉시 실패시킵니다.</p>
     *
     * @param eqpKey tc_eqp_socket.eqp_key (parent tc_eqp PK와 동일)
     * @return 조회된 parent tc_eqp
     */
    private TcEqp loadRequiredParentEqp(final long eqpKey) {
        return eqpMapper.findByEqpKey(eqpKey)
                .orElseThrow(() -> new IllegalStateException("tc_eqp_socket upsert 대상 parent tc_eqp 행이 없습니다. eqpKey=" + eqpKey));
    }

    /**
     * PASSIVE listener-group 검증용으로 tc_eqp 전체 목록을 페이징 스캔하여 수집합니다.
     *
     * <p>이 경로는 설비 등록/수정 시점에만 호출되므로, 조회 단순성과 구현 가독성을 우선하여
     * 별도 전용 쿼리 대신 기존 페이징 조회를 재사용합니다.</p>
     *
     * @return 검증용 tc_eqp 전체 목록
     */
    private List<TcEqp> loadAllEqpRowsForValidation() {
        final List<TcEqp> rows = new ArrayList<>();
        int offset = 0;

        while (true) {
            final List<TcEqp> page = eqpMapper.findAll(offset, VALIDATION_SCAN_PAGE_SIZE);
            rows.addAll(page);

            if (page.size() < VALIDATION_SCAN_PAGE_SIZE) {
                break;
            }
            offset += VALIDATION_SCAN_PAGE_SIZE;
        }

        if (log.isDebugEnabled()) {
            log.debug("PASSIVE SOCKET listener-group 검증용 tc_eqp 스캔 완료. totalEqpCount={}", rows.size());
        }
        return rows;
    }

    /**
     * SOCKET PASSIVE listener-group 비교 후보(기본 키: interface + mode + ip + port) 여부를 판정합니다.
     *
     * <p>SOCKET의 최종 listener-group 판정에는 {@code socket_protocol_type}까지 필요하므로,
     * 본 메서드는 parent tc_eqp 공통 컬럼만으로 1차 후보를 좁히는 역할만 담당합니다.</p>
     *
     * @param current 현재 저장 대상 parent tc_eqp
     * @param peer 비교 대상 peer tc_eqp
     * @return 1차 후보 여부
     */
    private boolean isPassiveSocketBaseCandidate(final TcEqp current, final TcEqp peer) {
        return peer != null
                && peer.commInterface() == ProtocolType.SOCKET
                && PASSIVE_MODE.equals(peer.commMode())
                && sameText(current.eqpIp(), peer.eqpIp())
                && current.eqpPort() == peer.eqpPort();
    }

    /**
     * 문자열 비교를 공백/대소문자 차이에 덜 민감하게 수행합니다.
     *
     * @param left 좌측 값
     * @param right 우측 값
     * @return 정규화 후 동일 여부
     */
    private boolean sameText(final String left, final String right) {
        return normalize(left).equals(normalize(right));
    }

    /**
     * 문자열 비교용 정규화 처리(앞뒤 공백 제거 + 대문자화)를 수행합니다.
     *
     * @param value 원본 문자열
     * @return 정규화 결과 (null이면 빈 문자열)
     */
    private String normalize(final String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpSocket> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     */
    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket deleteByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket deleteByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }
}
