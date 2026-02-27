package com.nori.tc.comm.gateway.runtime.mailbox;

import com.nori.tc.comm.gateway.config.props.GatewayRuntimeProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.gateway.runtime.channel.EquipmentChannel;
import com.nori.tc.comm.gateway.runtime.context.EquipmentRuntimeContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 설비 ID(`eqpId`) 기준 {@link EquipmentMailbox} 레지스트리입니다.
 *
 * <p>책임 범위:</p>
 * <p>1) BOUND 시 mailbox 생성 및 채널 바인딩</p>
 * <p>2) 설비별 queue 용량 정책 적용(inbound/outbound bounded queue 생성)</p>
 * <p>3) {@link EquipmentRuntimeContextFactory}를 이용한 런타임 컨텍스트 생성</p>
 * <p>4) UNBOUND/종료 시 mailbox 제거</p>
 *
 * <p>중요: 중복 연결로 동일 eqpId에 mailbox가 이미 존재하면 예외를 발생시켜 상위에서 처리하도록 합니다.</p>
 */
public final class EquipmentMailboxRegistry {

    private static final Logger log = LoggerFactory.getLogger(EquipmentMailboxRegistry.class);
    private final EquipmentRuntimeContextFactory contextFactory;
    private final GatewayRuntimeProperties runtimeProperties;
    private final Map<String, EquipmentMailbox> mailboxes = new ConcurrentHashMap<>();

    /**
     * mailbox 레지스트리 의존성을 초기화합니다.
     *
     * @param contextFactory 설비 메타 정보 -> 런타임 컨텍스트 생성 팩토리
     * @param runtimeProperties inbound/outbound 큐 용량 등 런타임 설정
     */
    public EquipmentMailboxRegistry(
            final EquipmentRuntimeContextFactory contextFactory,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");

        log.info(
                "EquipmentMailboxRegistry initialized. inboundQueueCapacity={}, outboundQueueCapacity={}",
                runtimeProperties.getInboundQueueCapacity(),
                runtimeProperties.getOutboundQueueCapacity()
        );
    }

    /**
     * 설비 ID로 등록된 mailbox를 조회합니다.
     *
     * <p>없으면 {@code null}을 반환합니다. 조회 실패는 정상 흐름(예: UNBOUND 상태)일 수 있으므로
     * 예외를 발생시키지 않습니다.</p>
     *
     * @param eqpId 설비 ID
     * @return 등록된 mailbox, 없으면 {@code null}
     */
    public EquipmentMailbox get(final String eqpId) {
        final EquipmentMailbox mailbox = mailboxes.get(eqpId);
        if (mailbox == null && log.isDebugEnabled()) {
            log.debug("Mailbox lookup missed. eqpId={}", eqpId);
        }
        return mailbox;
    }

    /**
     * 설비 메타 정보와 연결 채널로 신규 mailbox를 생성하고 레지스트리에 등록합니다.
     *
     * <p>처리 순서:</p>
     * <p>1) inbound/outbound bounded queue 생성</p>
     * <p>2) 공유 inbound queue 기반 런타임 컨텍스트 생성</p>
     * <p>3) mailbox 생성 및 채널 바인딩</p>
     * <p>4) 중복 여부 검사 후 레지스트리 등록</p>
     *
     * @param info 설비 메타 정보(DB 조회 결과)
     * @param channel 설비 연결 채널
     * @return 생성/등록된 mailbox
     */
    public EquipmentMailbox createAndBind(final GatewayEquipmentInfo info, final EquipmentChannel channel) {
        Objects.requireNonNull(info, "info is null");
        Objects.requireNonNull(channel, "channel is null");

        final String eqpId = info.equipmentId();

        // mailbox와 runtime context가 동일한 inbound 큐를 공유해야 파이프라인 적재/처리 간 데이터 불일치가 없습니다.
        final BoundedInboundQueue inboundQueue = new BoundedInboundQueue(runtimeProperties.getInboundQueueCapacity());
        final EquipmentRuntimeContext ctx = contextFactory.create(info, inboundQueue);
        final BoundedOutboundQueue outboundQueue = new BoundedOutboundQueue(runtimeProperties.getOutboundQueueCapacity());

        final EquipmentMailbox mailbox = new EquipmentMailbox(
                eqpId,
                info.commInterfaceType(),
                ctx,
                inboundQueue,
                outboundQueue
        );

        mailbox.bindChannel(channel);

        final EquipmentMailbox existing = mailboxes.putIfAbsent(eqpId, mailbox);
        if (existing != null) {
            log.warn("Mailbox create rejected because mailbox already exists. eqpId={}", eqpId);
            throw new IllegalStateException("Mailbox already exists for eqpId=" + eqpId);
        }

        log.info("Mailbox created and bound. eqpId={}, interfaceType={}", eqpId, info.commInterfaceType());
        return mailbox;
    }

    /**
     * 설비 mailbox를 레지스트리에서 제거합니다.
     *
     * <p>없는 eqpId 제거 요청은 예외 없이 무시합니다.</p>
     *
     * @param eqpId 설비 ID
     */
    public void remove(final String eqpId) {
        if (eqpId == null) {
            return;
        }
        final EquipmentMailbox removed = mailboxes.remove(eqpId);
        if (removed == null) {
            if (log.isDebugEnabled()) {
                log.debug("Mailbox remove skipped because mailbox does not exist. eqpId={}", eqpId);
            }
            return;
        }
        log.info("GW_MBX_UNBOUND. reason=REMOVE, eqpId={}", eqpId);
    }

    /**
     * 설비 mailbox를 "eqpId + 현재 바인딩 채널 인스턴스 일치" 조건으로만 제거합니다.
     *
     * <p>빠른 재연결 시 이전 채널의 늦은 disconnect 처리로 인해
     * 새 채널이 이미 바인딩한 mailbox가 잘못 제거되는 레이스를 방지하기 위한 메서드입니다.</p>
     *
     * <p>동작 규칙:</p>
     * <p>1) eqpId에 mailbox가 없으면 false 반환</p>
     * <p>2) mailbox.channel()이 expectedChannel과 동일 인스턴스가 아니면 false 반환</p>
     * <p>3) 동일 mailbox 인스턴스 매핑일 때만 remove(eqpId, mailbox)로 안전 제거</p>
     *
     * @param eqpId 설비 ID
     * @param expectedChannel 제거를 기대하는 현재 채널 인스턴스
     * @return 실제로 mailbox가 제거되었으면 true
     */
    public boolean removeIfChannelMatches(final String eqpId, final EquipmentChannel expectedChannel) {
        if (eqpId == null || eqpId.isBlank()) {
            return false;
        }
        Objects.requireNonNull(expectedChannel, "expectedChannel is null");

        final EquipmentMailbox mailbox = mailboxes.get(eqpId);
        if (mailbox == null) {
            if (log.isDebugEnabled()) {
                log.debug("Mailbox remove(match) skipped because mailbox does not exist. eqpId={}", eqpId);
            }
            return false;
        }

        if (mailbox.channel() != expectedChannel) {
            if (log.isDebugEnabled()) {
                log.debug("Mailbox remove(match) skipped because channel does not match current mailbox binding. eqpId={}",
                        eqpId);
            }
            return false;
        }

        final boolean removed = mailboxes.remove(eqpId, mailbox);
        if (removed) {
            log.info("GW_MBX_UNBOUND. reason=REMOVE_MATCH, eqpId={}", eqpId);
            return true;
        }

        if (log.isDebugEnabled()) {
            log.debug("Mailbox remove(match) race detected. eqpId={}", eqpId);
        }
        return false;
    }
}
