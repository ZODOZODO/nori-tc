package com.nori.tc.db.jpa.common.store.eqp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpHsms;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpEntity;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpHsmsEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpHsmsEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpJpaRepository;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpHsmsJpaRepository;

/**
 * tc_eqp_hsms JPA Store 구현체.
 *
 * <p>
 * <b>특이사항:</b>
 * HSMS 설정값(t3~t8, interval 등)이 많아 수동 매핑 시 실수가 잦은 영역입니다.
 * MapStruct를 통해 Command -> Entity 매핑을 100% 자동화했습니다.
 * </p>
 */
@Repository
public class TcEqpHsmsJpaStore implements TcEqpHsmsStore {

    private static final Logger log = LoggerFactory.getLogger(TcEqpHsmsJpaStore.class);
    private static final String PASSIVE_MODE = "PASSIVE";

    private final TcEqpHsmsJpaRepository repository;
    private final TcEqpJpaRepository eqpRepository;
    private final TcEqpHsmsEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param eqpRepository parent tc_eqp 조회/검증에 사용하는 JPA Repository
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpHsmsJpaStore(
            TcEqpHsmsJpaRepository repository,
            TcEqpJpaRepository eqpRepository,
            TcEqpHsmsEntityMapper mapper
    ) {
        this.repository = repository;
        this.eqpRepository = eqpRepository;
        this.mapper = mapper;
        log.info("tc_eqp_hsms JPA Store 초기화 완료. PASSIVE listener-group route_partition 검증이 활성화됩니다.");
    }

    
    /**
     * DB JPA 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    @Override
    @Transactional
    public TcEqpHsms upsert(UpsertTcEqpHsms command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);
        validatePassiveHsmsListenerGroupRoutePartition(command);

        try {
            final long eqpKey = command.eqpKey();

            // 1. 조회 또는 신규 생성
            final TcEqpHsmsEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpHsmsEntity.newEntity(eqpKey));

            // 2. [MapStruct] 전체 필드 자동 매핑
            mapper.updateEntity(command, entity);

            // 3. 저장 및 반환
            TcEqpHsmsEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_hsms] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] upsert failed", e);
        }
    }

    /**
     * PASSIVE HSMS 설비의 listener-group(interface=HSMS + eqp_ip + eqp_port) route_partition 동일성 규칙을 검증합니다.
     *
     * <p>검증 목적:</p>
     * <p>- 동일 HSMS passive listener 주소를 공유하는 설비들의 route_partition 불일치를 저장 전에 차단합니다.</p>
     *
     * @param command HSMS 상세 설정 upsert 입력
     */
    private void validatePassiveHsmsListenerGroupRoutePartition(final UpsertTcEqpHsms command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        try {
            final TcEqpEntity parent = eqpRepository.findById(command.eqpKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "tc_eqp_hsms upsert 대상 parent tc_eqp 행이 없습니다. eqpKey=" + command.eqpKey()));

            if (!PASSIVE_MODE.equals(parent.getCommMode())) {
                if (log.isDebugEnabled()) {
                    log.debug("PASSIVE HSMS listener-group route_partition 검증을 건너뜁니다. 사유=ACTIVE 설비, eqpId={}, eqpKey={}, commMode={}",
                            parent.getEqpId(),
                            parent.getEqpKey(),
                            parent.getCommMode());
                }
                return;
            }

            if (parent.getCommInterface() != ProtocolType.HSMS) {
                throw new IllegalStateException("tc_eqp_hsms upsert 대상의 parent comm_interface가 HSMS가 아닙니다. eqpKey="
                        + command.eqpKey() + ", commInterface=" + parent.getCommInterface());
            }

            if (log.isDebugEnabled()) {
                log.debug("PASSIVE HSMS listener-group route_partition 검증 시작. eqpId={}, eqpKey={}, routePartition={}, bind={}:{}, deviceId={}",
                        parent.getEqpId(),
                        parent.getEqpKey(),
                        parent.getRoutePartition(),
                        parent.getEqpIp(),
                        parent.getEqpPort(),
                        command.deviceId());
            }

            final List<TcEqpEntity> allEqps = eqpRepository.findAll();
            if (log.isDebugEnabled()) {
                log.debug("PASSIVE HSMS listener-group 검증용 tc_eqp 전체 조회 완료. totalEqpCount={}", allEqps.size());
            }

            int candidateCount = 0;
            for (TcEqpEntity peer : allEqps) {
                if (!isPassiveHsmsCandidate(parent, peer)) {
                    continue;
                }
                if (Objects.equals(parent.getEqpKey(), peer.getEqpKey())) {
                    continue;
                }

                candidateCount++;

                if (!Objects.equals(parent.getRoutePartition(), peer.getRoutePartition())) {
                    log.warn("PASSIVE HSMS listener-group route_partition 불일치 감지. currentEqpId={}, currentEqpKey={}, currentRoutePartition={}, "
                                    + "peerEqpId={}, peerEqpKey={}, peerRoutePartition={}, bind={}:{}, deviceId={}",
                            parent.getEqpId(),
                            parent.getEqpKey(),
                            parent.getRoutePartition(),
                            peer.getEqpId(),
                            peer.getEqpKey(),
                            peer.getRoutePartition(),
                            parent.getEqpIp(),
                            parent.getEqpPort(),
                            command.deviceId());
                    throw new IllegalArgumentException("동일 PASSIVE HSMS listener-group의 route_partition은 동일해야 합니다. "
                            + "currentEqpId=" + parent.getEqpId() + ", peerEqpId=" + peer.getEqpId());
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("PASSIVE HSMS listener-group route_partition 검증 완료. eqpId={}, eqpKey={}, 후보수={}",
                        parent.getEqpId(),
                        parent.getEqpKey(),
                        candidateCount);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] PASSIVE listener-group route_partition 검증 실패", e);
        }
    }

    /**
     * HSMS listener-group 후보(HSMS + PASSIVE + 동일 bind ip/port) 여부를 판정합니다.
     *
     * @param current 현재 저장 대상 parent tc_eqp
     * @param peer 비교 대상 peer tc_eqp
     * @return 후보 여부
     */
    private boolean isPassiveHsmsCandidate(final TcEqpEntity current, final TcEqpEntity peer) {
        return peer != null
                && peer.getCommInterface() == ProtocolType.HSMS
                && PASSIVE_MODE.equals(peer.getCommMode())
                && sameText(current.getEqpIp(), peer.getEqpIp())
                && Objects.equals(current.getEqpPort(), peer.getEqpPort());
    }

    /**
     * 문자열 비교를 공백/대소문자 차이에 덜 민감하게 수행합니다.
     *
     * @param left 좌측 문자열
     * @param right 우측 문자열
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
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpHsms> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] findByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     */
    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpHsms command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.deviceId() < 0) throw new IllegalArgumentException("command.deviceId must be >= 0");
        if (command.t3Timeout() <= 0) throw new IllegalArgumentException("command.t3Timeout must be > 0");
        if (command.t5Timeout() <= 0) throw new IllegalArgumentException("command.t5Timeout must be > 0");
        if (command.t6Timeout() <= 0) throw new IllegalArgumentException("command.t6Timeout must be > 0");
        if (command.t7Timeout() <= 0) throw new IllegalArgumentException("command.t7Timeout must be > 0");
        if (command.t8Timeout() <= 0) throw new IllegalArgumentException("command.t8Timeout must be > 0");
        if (command.linkTestInterval() <= 0) throw new IllegalArgumentException("command.linkTestInterval must be > 0");
        if (command.maxMsgBytes() <= 0) throw new IllegalArgumentException("command.maxMsgBytes must be > 0");
    }
}
