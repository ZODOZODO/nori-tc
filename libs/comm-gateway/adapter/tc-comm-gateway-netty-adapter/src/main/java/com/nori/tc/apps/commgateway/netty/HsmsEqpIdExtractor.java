package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.hsms.secs.Secs2Decoder;
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

    public HsmsEqpIdExtractor(
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder
    ) {
        this.frameExtractor = frameExtractor;
        this.secs2Decoder = secs2Decoder;
    }

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
