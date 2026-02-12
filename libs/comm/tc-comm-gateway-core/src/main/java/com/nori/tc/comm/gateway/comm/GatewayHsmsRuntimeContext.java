package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.inbound.InboundQueue;
import com.nori.tc.comm.gateway.hsms.pipeline.HsmsRuntimeContext;
import com.nori.tc.comm.gateway.hsms.session.HsmsSessionStateMachine;

import java.util.Map;
import java.util.Objects;

/**
 * HSMS 설비용 런타임 컨텍스트
 */
public final class GatewayHsmsRuntimeContext implements HsmsRuntimeContext {

    private final EquipmentProfile profile;
    private final InboundQueue inboundQueue;
    private final ReassemblyBuffer reassemblyBuffer;
    private final Map<String, String> tags;
    private final HsmsSessionStateMachine hsmsSession;

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param profile 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param inboundQueue 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param reassemblyBuffer 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param tags 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param hsmsSession 통신 채널/세션 정보
     */
    public GatewayHsmsRuntimeContext(
            final EquipmentProfile profile,
            final InboundQueue inboundQueue,
            final ReassemblyBuffer reassemblyBuffer,
            final Map<String, String> tags,
            final HsmsSessionStateMachine hsmsSession
    ) {
        this.profile = Objects.requireNonNull(profile, "profile is null");
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue is null");
        this.reassemblyBuffer = Objects.requireNonNull(reassemblyBuffer, "reassemblyBuffer is null");
        this.tags = (tags == null) ? Map.of() : Map.copyOf(tags);
        this.hsmsSession = Objects.requireNonNull(hsmsSession, "hsmsSession is null");
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    @Override
    public EquipmentProfile profile() {
        return profile;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    @Override
    public InboundQueue inboundQueue() {
        return inboundQueue;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    @Override
    public ReassemblyBuffer reassemblyBuffer() {
        return reassemblyBuffer;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    @Override
    public Map<String, String> tags() {
        return tags;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    @Override
    public HsmsSessionStateMachine hsmsSession() {
        return hsmsSession;
    }
}
