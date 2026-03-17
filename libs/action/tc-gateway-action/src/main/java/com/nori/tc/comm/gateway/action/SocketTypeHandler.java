package com.nori.tc.comm.gateway.action;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

/**
 * socketType별 핸들러 - Gateway 플러그인 JAR 개발자가 구현해야 하는 핵심 계약
 *
 * <p>목표:</p>
 * <p>- socketType이 늘어나도, 각 타입의 encode/decode 로직이 "해당 디렉터리"에 모여 있게 합니다.</p>
 * <p>- tc-comm-gateway 유지보수성을 위해 가장 중요한 분리 지점입니다.</p>
 *
 * <p>책임 범위:</p>
 * <p>1) 프레임 추출: reassemblyBuffer에서 프레임 1개를 뽑는다(없으면 null)</p>
 * <p>2) decode: 추출된 프레임 bytes를 domain 메시지(메시지명 + body)로 변환한다</p>
 * <p>3) encode(옵션): outbound command를 raw bytes로 인코딩한다</p>
 *
 * <p>플러그인 JAR 개발 시:</p>
 * <pre>
 * // build.gradle.kts
 * dependencies {
 *     compileOnly(project(":libs:action:tc-gateway-action"))
 *     // 또는 Maven 좌표: compileOnly("com.nori.tc:tc-gateway-action:0.0.1-SNAPSHOT")
 * }
 *
 * // 구현 예시
 * public class MyEqpSocketTypeHandler implements SocketTypeHandler {
 *     {@literal @}Override
 *     public String socketType() { return "MY_EQP_PROTOCOL"; }
 *
 *     {@literal @}Override
 *     public SocketFrame tryExtractOne(ReassemblyBuffer buffer, int maxFrameBytes) { ... }
 *
 *     {@literal @}Override
 *     public SocketTypeDecodeResult decode(byte[] frameBytes) { ... }
 * }
 * </pre>
 *
 * <p>주의: 플러그인 JAR 1개당 이 인터페이스 구현체는 반드시 1개만 존재해야 합니다.
 * 0개이거나 2개 이상이면 런타임 로드 시 예외가 발생합니다.</p>
 */
public interface SocketTypeHandler {

    /**
     * 이 핸들러가 담당하는 socketType 문자열(예: "LINE_DELIMITED", "A", "B")
     */
    String socketType();

    /**
     * 프레임 1개 추출
     *
     * @param buffer reassembly buffer(누적된 수신 bytes)
     * @param maxFrameBytes 프레임 상한(폭주 방어)
     * @return 프레임 1개, 아직 부족하면 null
     */
    SocketFrame tryExtractOne(ReassemblyBuffer buffer, int maxFrameBytes);

    /**
     * 프레임 decode
     *
     * @param frameBytes 프레임 bytes
     * @return decode 결과(메시지명 + body 등)
     */
    SocketTypeDecodeResult decode(byte[] frameBytes);

    /**
     * outbound command encode(옵션)
     *
     * <p>실제로 socket outbound를 사용하지 않는다면, 구현에서 UnsupportedOperationException을 던져도 됩니다.</p>
     */
    default SocketTypeEncodeResult encode(final Object command) {
        throw new UnsupportedOperationException("encode() is not supported for socketType=" + socketType());
    }
}
