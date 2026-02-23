package com.nori.tc.comm.gateway.runtime.context;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.inbound.InboundQueue;
import com.nori.tc.comm.gateway.socket.config.SocketTypeConfig;
import com.nori.tc.comm.gateway.socket.runtime.SocketRuntimeContext;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * SOCKET 설비 전용 런타임 컨텍스트 구현체입니다.
 *
 * <p>공통 런타임 정보에 더해 socketType 설정과 {@link SocketTypeRegistry}를 함께 제공하여
 * SOCKET 파이프라인이 설비별 프레임 추출/인코딩/디코딩 전략을 결정할 수 있도록 합니다.</p>
 */
public final class SocketEquipmentRuntimeContext implements SocketRuntimeContext {

    private final EquipmentProfile profile;
    private final InboundQueue inboundQueue;
    private final ReassemblyBuffer reassemblyBuffer;
    private final Map<String, String> tags;
    private final SocketTypeConfig socketTypeConfig;
    private final SocketTypeRegistry socketTypeRegistry;

    /**
     * SOCKET 런타임 컨텍스트를 생성합니다.
     *
     * @param profile 설비 공통 프로파일
     * @param inboundQueue 설비별 inbound 큐(메일박스와 공유)
     * @param reassemblyBuffer 수신 프레임 재조립 버퍼
     * @param tags 로그/메트릭/DLQ용 공통 태그
     * @param socketTypeConfig 설비별 socketType 해석 설정
     * @param socketTypeRegistry socketType 핸들러 레지스트리
     */
    public SocketEquipmentRuntimeContext(
            final EquipmentProfile profile,
            final InboundQueue inboundQueue,
            final ReassemblyBuffer reassemblyBuffer,
            final Map<String, String> tags,
            final SocketTypeConfig socketTypeConfig,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.profile = Objects.requireNonNull(profile, "profile is null");
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue is null");
        this.reassemblyBuffer = Objects.requireNonNull(reassemblyBuffer, "reassemblyBuffer is null");
        this.tags = (tags == null) ? Map.of() : Map.copyOf(tags);
        this.socketTypeConfig = Objects.requireNonNull(socketTypeConfig, "socketTypeConfig is null");
        this.socketTypeRegistry = Objects.requireNonNull(socketTypeRegistry, "socketTypeRegistry is null");
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
     * 설비별 socketType 해석 설정을 반환합니다.
     *
     * @return socketType 설정
     */
    @Override
    public SocketTypeConfig socketTypeConfig() {
        return socketTypeConfig;
    }

    /**
     * socketType 핸들러 레지스트리를 반환합니다.
     *
     * @return socketType 레지스트리
     */
    @Override
    public SocketTypeRegistry socketTypeRegistry() {
        return socketTypeRegistry;
    }
}
