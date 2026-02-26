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

import com.nori.tc.db.core.eqp.store.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocket;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpEntity;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpSocketEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpSocketEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpJpaRepository;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpSocketJpaRepository;

/**
 * tc_eqp_socket JPA Store 구현체.
 *
 * <p>
 * <b>Charset 정책:</b>
 * DB 컬럼은 NOT NULL입니다. 커맨드로 들어온 charset이 null/blank인 경우,
 * 애플리케이션 레벨에서 기본값(UTF-8)을 강제하여 DB 제약을 만족시킵니다.
 * </p>
 */
@Repository
public class TcEqpSocketJpaStore implements TcEqpSocketStore {

    private static final Logger log = LoggerFactory.getLogger(TcEqpSocketJpaStore.class);
    private static final String DEFAULT_CHARSET = "UTF-8";
    private static final String PASSIVE_MODE = "PASSIVE";

    private final TcEqpSocketJpaRepository repository;
    private final TcEqpJpaRepository eqpRepository;
    private final TcEqpSocketEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param eqpRepository parent tc_eqp 조회/검증에 사용하는 JPA Repository
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpSocketJpaStore(
            TcEqpSocketJpaRepository repository,
            TcEqpJpaRepository eqpRepository,
            TcEqpSocketEntityMapper mapper
    ) {
        this.repository = repository;
        this.eqpRepository = eqpRepository;
        this.mapper = mapper;
        log.info("tc_eqp_socket JPA Store 초기화 완료. PASSIVE listener-group route_partition 검증이 활성화됩니다.");
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
    public TcEqpSocket upsert(UpsertTcEqpSocket command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);
        validatePassiveSocketListenerGroupRoutePartition(command);

        try {
            final long eqpKey = command.eqpKey();

            final TcEqpSocketEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpSocketEntity.newEntity(eqpKey));

            // 1. [MapStruct] 일반 필드 매핑
            mapper.updateEntity(command, entity);

            // 2. [Manual Logic] Charset 기본값 처리
            final String charset = (command.charset() == null || command.charset().isBlank())
                    ? DEFAULT_CHARSET
                    : command.charset();
            entity.setCharset(charset);

            // 3. 저장 및 반환
            TcEqpSocketEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_socket] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket] upsert failed", e);
        }
    }

    /**
     * PASSIVE SOCKET 설비의 listener-group(route key) 기준 route_partition 동일성 규칙을 검증합니다.
     *
     * <p>검증 기준:</p>
     * <p>- parent tc_eqp.comm_mode = PASSIVE</p>
     * <p>- parent tc_eqp.comm_interface = SOCKET</p>
     * <p>- 동일 eqp_ip + eqp_port + socket_protocol_type를 공유하는 다른 설비와 route_partition 동일</p>
     *
     * @param command SOCKET 상세 설정 upsert 입력
     */
    private void validatePassiveSocketListenerGroupRoutePartition(final UpsertTcEqpSocket command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        try {
            final TcEqpEntity parent = eqpRepository.findById(command.eqpKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "tc_eqp_socket upsert 대상 parent tc_eqp 행이 없습니다. eqpKey=" + command.eqpKey()));

            if (!PASSIVE_MODE.equals(parent.getCommMode())) {
                if (log.isDebugEnabled()) {
                    log.debug("PASSIVE SOCKET listener-group route_partition 검증을 건너뜁니다. 사유=ACTIVE 설비, eqpId={}, eqpKey={}, commMode={}",
                            parent.getEqpId(),
                            parent.getEqpKey(),
                            parent.getCommMode());
                }
                return;
            }

            if (parent.getCommInterface() != ProtocolType.SOCKET) {
                throw new IllegalStateException("tc_eqp_socket upsert 대상의 parent comm_interface가 SOCKET이 아닙니다. eqpKey="
                        + command.eqpKey() + ", commInterface=" + parent.getCommInterface());
            }

            if (log.isDebugEnabled()) {
                log.debug("PASSIVE SOCKET listener-group route_partition 검증 시작. eqpId={}, eqpKey={}, routePartition={}, bind={}:{}, socketProtocolType={}",
                        parent.getEqpId(),
                        parent.getEqpKey(),
                        parent.getRoutePartition(),
                        parent.getEqpIp(),
                        parent.getEqpPort(),
                        command.socketProtocolType());
            }

            final List<TcEqpEntity> allEqps = eqpRepository.findAll();
            if (log.isDebugEnabled()) {
                log.debug("PASSIVE SOCKET listener-group 검증용 tc_eqp 전체 조회 완료. totalEqpCount={}", allEqps.size());
            }

            int candidateCount = 0;
            for (TcEqpEntity peer : allEqps) {
                if (!isPassiveSocketBaseCandidate(parent, peer)) {
                    continue;
                }
                if (Objects.equals(parent.getEqpKey(), peer.getEqpKey())) {
                    continue;
                }

                final Long peerEqpKey = peer.getEqpKey();
                final TcEqpSocketEntity peerSocket = repository.findById(peerEqpKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "PASSIVE SOCKET route_partition 검증 중 peer tc_eqp_socket 행이 없습니다. peerEqpKey=" + peerEqpKey));

                if (!sameText(command.socketProtocolType(), peerSocket.getSocketProtocolType())) {
                    continue;
                }

                candidateCount++;

                if (!Objects.equals(parent.getRoutePartition(), peer.getRoutePartition())) {
                    log.warn("PASSIVE SOCKET listener-group route_partition 불일치 감지. currentEqpId={}, currentEqpKey={}, currentRoutePartition={}, "
                                    + "peerEqpId={}, peerEqpKey={}, peerRoutePartition={}, bind={}:{}, socketProtocolType={}",
                            parent.getEqpId(),
                            parent.getEqpKey(),
                            parent.getRoutePartition(),
                            peer.getEqpId(),
                            peer.getEqpKey(),
                            peer.getRoutePartition(),
                            parent.getEqpIp(),
                            parent.getEqpPort(),
                            command.socketProtocolType());
                    throw new IllegalArgumentException("동일 PASSIVE SOCKET listener-group의 route_partition은 동일해야 합니다. "
                            + "currentEqpId=" + parent.getEqpId() + ", peerEqpId=" + peer.getEqpId());
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("PASSIVE SOCKET listener-group route_partition 검증 완료. eqpId={}, eqpKey={}, 후보수={}",
                        parent.getEqpId(),
                        parent.getEqpKey(),
                        candidateCount);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket] PASSIVE listener-group route_partition 검증 실패", e);
        }
    }

    /**
     * SOCKET PASSIVE listener-group 1차 후보(interface + mode + ip + port) 여부를 판정합니다.
     *
     * <p>최종 listener-group 비교에는 {@code socket_protocol_type}가 추가로 필요하므로,
     * 본 메서드는 tc_eqp 공통 컬럼만으로 1차 후보만 선별합니다.</p>
     *
     * @param current 현재 저장 대상 parent tc_eqp
     * @param peer 비교 대상 peer tc_eqp
     * @return 1차 후보 여부
     */
    private boolean isPassiveSocketBaseCandidate(final TcEqpEntity current, final TcEqpEntity peer) {
        return peer != null
                && peer.getCommInterface() == ProtocolType.SOCKET
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
    public Optional<TcEqpSocket> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket] findByEqpKey failed: eqpKey=" + eqpKey, e);
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
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpSocket command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
        if (command.socketProtocolType() == null || command.socketProtocolType().isBlank()) throw new IllegalArgumentException("command.socketProtocolType must not be null/blank");
    }
}
