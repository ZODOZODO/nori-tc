package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.gateway.config.props.GatewayRuntimeProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * eqpId -> EqpMailbox registry.
 *
 * - Mailbox는 BOUND 이후 생성됩니다.
 * - 연결 종료 시 mailbox 제거를 권장합니다.
 */
public final class EqpMailboxRegistry {

    private static final Logger log = LoggerFactory.getLogger(EqpMailboxRegistry.class);
    private final EquipmentContextFactory contextFactory;
    private final GatewayRuntimeProperties runtimeProperties;
    private final Map<String, EqpMailbox> mailboxes = new ConcurrentHashMap<>();

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param contextFactory 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param runtimeProperties 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public EqpMailboxRegistry(
            final EquipmentContextFactory contextFactory,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public EqpMailbox get(final String eqpId) {
        return mailboxes.get(eqpId);
    }

    
    /**
     * 게이트웨이 코어 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param info 도메인 데이터 객체
     * @param channel 통신 채널/세션 정보
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public EqpMailbox createAndBind(final GatewayEquipmentInfo info, final EquipmentChannel channel) {
        Objects.requireNonNull(info, "info is null");
        Objects.requireNonNull(channel, "channel is null");

        final String eqpId = info.equipmentId();
        final BoundedInboundQueue inboundQueue = new BoundedInboundQueue(runtimeProperties.getInboundQueueCapacity());
        final EquipmentRuntimeContext ctx = contextFactory.create(info, inboundQueue);
        final BoundedOutboundQueue outboundQueue = new BoundedOutboundQueue(runtimeProperties.getOutboundQueueCapacity());

        final EqpMailbox mailbox = new EqpMailbox(
                eqpId,
                info.commInterfaceType(),
                ctx,
                inboundQueue,
                outboundQueue
        );

        mailbox.bindChannel(channel);

        final EqpMailbox existing = mailboxes.putIfAbsent(eqpId, mailbox);
        if (existing != null) {
            throw new IllegalStateException("Mailbox already exists for eqpId=" + eqpId);
        }

        log.info("Mailbox created and bound. eqpId={}, interfaceType={}", eqpId, info.commInterfaceType());
        return mailbox;
    }

    
    /**
     * 게이트웨이 코어 모듈 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     */
    public void remove(final String eqpId) {
        if (eqpId == null) {
            return;
        }
        mailboxes.remove(eqpId);
        log.info("Mailbox removed. eqpId={}", eqpId);
    }
}
