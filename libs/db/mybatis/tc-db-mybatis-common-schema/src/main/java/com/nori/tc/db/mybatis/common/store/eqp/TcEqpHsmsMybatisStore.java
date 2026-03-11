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

import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpHsms;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpMapper;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpHsmsMapper;

/**
 * tc_eqp_hsms MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_key)
 * - created_at/updated_at은 DB/SQL이 관리(CURRENT_TIMESTAMP)하므로
 *   command의 createdAt/updatedAt은 "입력 DTO"로만 보관되고 실제 SQL에서는 반영되지 않을 수 있다.
 */
@Repository
public class TcEqpHsmsMybatisStore implements TcEqpHsmsStore {

    private static final Logger log = LoggerFactory.getLogger(TcEqpHsmsMybatisStore.class);
    private static final String PASSIVE_MODE = "PASSIVE";
    private static final int VALIDATION_SCAN_PAGE_SIZE = 500;

    private final TcEqpHsmsMapper mapper;
    private final TcEqpMapper eqpMapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     * @param eqpMapper parent tc_eqp 조회/검증에 사용하는 MyBatis 매퍼
     */
    public TcEqpHsmsMybatisStore(TcEqpHsmsMapper mapper, TcEqpMapper eqpMapper) {
        this.mapper = mapper;
        this.eqpMapper = eqpMapper;
        log.info("tc_eqp_hsms MyBatis Store 초기화 완료. PASSIVE listener-group route_partition 검증이 활성화됩니다.");
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
    public TcEqpHsms upsert(UpsertTcEqpHsms command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final long eqpKey = command.eqpKey();
        validatePassiveHsmsListenerGroupRoutePartition(command);

        final TcEqpHsms row = new TcEqpHsms(
                eqpKey,
                command.deviceId(),
                command.t3Timeout(),
                command.t5Timeout(),
                command.t6Timeout(),
                command.t7Timeout(),
                command.t8Timeout(),
                command.linkTestEnabled(),
                command.linkTestInterval(),
                command.maxMsgBytes(),
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
                    .orElseThrow(() -> new DbAccessException("tc_eqp_hsms upsert succeeded but row not found. eqpKey=" + eqpKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_hsms upsert duplicate key. eqpKey=" + eqpKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms upsert failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms upsert failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    /**
     * PASSIVE HSMS 설비의 listener-group(interface=HSMS + eqp_ip + eqp_port) route_partition 동일성 규칙을 검증합니다.
     *
     * <p>검증 목적:</p>
     * <p>- 동일 HSMS passive listener 주소를 공유하는 설비들이 서로 다른 route_partition으로 등록되는 것을 차단합니다.</p>
     *
     * <p>검증 시점:</p>
     * <p>- {@link #upsert(UpsertTcEqpHsms)} 저장 직전</p>
     *
     * @param command HSMS 상세 설정 upsert 요청
     */
    private void validatePassiveHsmsListenerGroupRoutePartition(final UpsertTcEqpHsms command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        try {
            final TcEqp parent = loadRequiredParentEqp(command.eqpKey());

            if (!PASSIVE_MODE.equals(parent.commMode())) {
                if (log.isDebugEnabled()) {
                    log.debug("PASSIVE HSMS listener-group route_partition 검증을 건너뜁니다. 사유=ACTIVE 설비, eqpId={}, eqpKey={}, commMode={}",
                            parent.eqpId(),
                            parent.eqpKey(),
                            parent.commMode());
                }
                return;
            }

            if (parent.commInterface() != ProtocolType.SECS) {
                throw new IllegalStateException("tc_eqp_hsms upsert 대상의 parent comm_interface가 HSMS가 아닙니다. eqpKey="
                        + command.eqpKey() + ", commInterface=" + parent.commInterface());
            }

            if (log.isDebugEnabled()) {
                log.debug("PASSIVE HSMS listener-group route_partition 검증 시작. eqpId={}, eqpKey={}, routePartition={}, bind={}:{}, deviceId={}",
                        parent.eqpId(),
                        parent.eqpKey(),
                        parent.routePartition(),
                        parent.eqpIp(),
                        parent.eqpPort(),
                        command.deviceId());
            }

            int candidateCount = 0;
            for (TcEqp peer : loadAllEqpRowsForValidation()) {
                if (!isPassiveHsmsCandidate(parent, peer)) {
                    continue;
                }
                if (Objects.equals(parent.eqpKey(), peer.eqpKey())) {
                    continue;
                }

                candidateCount++;

                if (!Objects.equals(parent.routePartition(), peer.routePartition())) {
                    log.warn("PASSIVE HSMS listener-group route_partition 불일치 감지. currentEqpId={}, currentEqpKey={}, currentRoutePartition={}, "
                                    + "peerEqpId={}, peerEqpKey={}, peerRoutePartition={}, bind={}:{}, deviceId={}",
                            parent.eqpId(),
                            parent.eqpKey(),
                            parent.routePartition(),
                            peer.eqpId(),
                            peer.eqpKey(),
                            peer.routePartition(),
                            parent.eqpIp(),
                            parent.eqpPort(),
                            command.deviceId());
                    throw new IllegalArgumentException("동일 PASSIVE HSMS listener-group의 route_partition은 동일해야 합니다. "
                            + "currentEqpId=" + parent.eqpId() + ", peerEqpId=" + peer.eqpId());
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("PASSIVE HSMS listener-group route_partition 검증 완료. eqpId={}, eqpKey={}, 후보수={}",
                        parent.eqpId(),
                        parent.eqpKey(),
                        candidateCount);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms PASSIVE listener-group route_partition 검증 실패(DB). eqpKey=" + command.eqpKey(), e);
        } catch (DbAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms PASSIVE listener-group route_partition 검증 실패. eqpKey=" + command.eqpKey(), e);
        }
    }

    /**
     * 검증 대상 parent(tc_eqp) 행을 조회합니다.
     *
     * @param eqpKey tc_eqp_hsms.eqp_key
     * @return parent tc_eqp
     */
    private TcEqp loadRequiredParentEqp(final long eqpKey) {
        return eqpMapper.findByEqpKey(eqpKey)
                .orElseThrow(() -> new IllegalStateException("tc_eqp_hsms upsert 대상 parent tc_eqp 행이 없습니다. eqpKey=" + eqpKey));
    }

    /**
     * PASSIVE HSMS listener-group 검증용으로 tc_eqp 전체 목록을 페이징 조회합니다.
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
            log.debug("PASSIVE HSMS listener-group 검증용 tc_eqp 스캔 완료. totalEqpCount={}", rows.size());
        }
        return rows;
    }

    /**
     * HSMS listener-group 후보(HSMS + PASSIVE + 동일 bind ip/port) 여부를 판정합니다.
     *
     * @param current 현재 저장 대상 parent tc_eqp
     * @param peer 비교 대상 peer tc_eqp
     * @return 후보 여부
     */
    private boolean isPassiveHsmsCandidate(final TcEqp current, final TcEqp peer) {
        return peer != null
                && peer.commInterface() == ProtocolType.SECS
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
    public Optional<TcEqpHsms> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
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
            throw new DbAccessException("tc_eqp_hsms deleteByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms deleteByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }
}
