package com.nori.tc.comm.gateway.runtime.context;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.inbound.InboundQueue;
import com.nori.tc.comm.gateway.hsms.runtime.HsmsRuntimeContext;
import com.nori.tc.comm.gateway.hsms.session.HsmsSessionStateMachine;

import java.util.Map;
import java.util.Objects;

/**
 * HSMS 설비 전용 런타임 컨텍스트 구현체입니다.
 *
 * <p>공통 런타임 정보({@link EquipmentProfile}, inbound queue, reassembly buffer, tags)에 더해
 * HSMS 세션 상태 머신을 함께 보관합니다.</p>
 */
public final class HsmsEquipmentRuntimeContext implements HsmsRuntimeContext {

    private final EquipmentProfile profile;
    private final InboundQueue inboundQueue;
    private final ReassemblyBuffer reassemblyBuffer;
    private final Map<String, String> tags;
    private final HsmsSessionStateMachine hsmsSession;

    /**
     * HSMS 런타임 컨텍스트를 생성합니다.
     *
     * @param profile 설비 공통 프로파일
     * @param inboundQueue 설비별 inbound 큐(메일박스와 공유)
     * @param reassemblyBuffer 수신 프레임 재조립 버퍼
     * @param tags 로그/메트릭/DLQ에 전달할 공통 태그
     * @param hsmsSession 설비별 HSMS 세션 상태 머신
     */
    public HsmsEquipmentRuntimeContext(
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
     * 설비 공통 프로파일을 반환합니다.
     *
     * @return 설비 프로파일
     */
    @Override
    public EquipmentProfile profile() {
        return profile;
    }

    /**
     * 메일박스와 공유하는 inbound 큐를 반환합니다.
     *
     * @return 설비별 inbound 큐
     */
    @Override
    public InboundQueue inboundQueue() {
        return inboundQueue;
    }

    /**
     * 수신 프레임 재조립 버퍼를 반환합니다.
     *
     * @return 재조립 버퍼
     */
    @Override
    public ReassemblyBuffer reassemblyBuffer() {
        return reassemblyBuffer;
    }

    /**
     * 공통 운영 태그를 반환합니다.
     *
     * @return 불변 태그 맵
     */
    @Override
    public Map<String, String> tags() {
        return tags;
    }

    /**
     * HSMS 세션 상태 머신을 반환합니다.
     *
     * @return 설비별 HSMS 세션 상태 머신
     */
    @Override
    public HsmsSessionStateMachine hsmsSession() {
        return hsmsSession;
    }
}
