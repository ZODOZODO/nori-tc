package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Decoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * HSMS S1F2 기반 eqpId 추출기.
 *
 * - UNBOUND 상태에서만 사용
 * - S1F2 payload에서 eqpId를 추출
 */
public final class HsmsEqpIdExtractor implements EqpIdExtractor {

    private static final Logger log = LoggerFactory.getLogger(HsmsEqpIdExtractor.class);

    private final HsmsFrameExtractor frameExtractor;
    private final Secs2Decoder secs2Decoder;

    
    /**
     * 게이트웨이 Netty 어댑터 구성 요소를 초기화합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param frameExtractor 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param secs2Decoder 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public HsmsEqpIdExtractor(
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder
    ) {
        this.frameExtractor = frameExtractor;
        this.secs2Decoder = secs2Decoder;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param buffer 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    public Optional<String> tryExtractEqpId(final ReassemblyBuffer buffer) {
        // TODO: HSMS S1F2 eqpId 추출 로직 확정 후 구현
        // 현재는 UNBOUND 단계에서 프레임만 소비하고 eqpId는 추출하지 않습니다.
        // (등록 전 메시지는 처리하지 않음/드롭 정책 유지)
        while (true) {
            final var frame = frameExtractor.tryExtractOne(buffer);
            if (frame == null) {
                return Optional.empty();
            }
            if (log.isDebugEnabled()) {
                log.debug("HSMS UNBOUND frame consumed (eqpId extraction not implemented).");
            }
            // TODO: S1F2 판정 후 eqpId 추출 구현
            // 지금은 모든 프레임을 소비하여 UNBOUND 상태의 메시지를 드롭합니다.
        }
    }

    // TODO: HSMS S1F2 payload format 확정 후 eqpId 파싱 로직 구현
}
