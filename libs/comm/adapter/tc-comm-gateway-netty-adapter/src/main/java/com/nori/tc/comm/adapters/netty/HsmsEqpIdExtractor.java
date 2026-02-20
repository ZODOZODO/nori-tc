package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Decoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Objects;

/**
 * HSMS UNBOUND 프레임에서 eqpId를 추출하기 위한 파서입니다.
 *
 * <p>현재는 HSMS S1F2 기반 eqpId 추출 규격이 확정되지 않아
 * 프레임을 소비만 하고 eqpId는 반환하지 않습니다.</p>
 */
public final class HsmsEqpIdExtractor implements EqpIdExtractor {

    private static final Logger log = LoggerFactory.getLogger(HsmsEqpIdExtractor.class);

    /**
     * HSMS raw frame 추출기입니다.
     */
    private final HsmsFrameExtractor frameExtractor;

    /**
     * 향후 S1F2 payload 파싱 구현 시 사용할 디코더입니다.
     */
    private final Secs2Decoder secs2Decoder;

    /**
     * HSMS eqpId 추출기를 초기화합니다.
     *
     * @param frameExtractor HSMS 프레임 추출기
     * @param secs2Decoder HSMS SECS-II 디코더(향후 사용)
     */
    public HsmsEqpIdExtractor(
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder
    ) {
        this.frameExtractor = Objects.requireNonNull(frameExtractor, "frameExtractor is null");
        this.secs2Decoder = Objects.requireNonNull(secs2Decoder, "secs2Decoder is null");
    }

    /**
     * UNBOUND 버퍼에서 HSMS 프레임을 소비하며 eqpId를 탐색합니다.
     *
     * <p>현 구현은 eqpId 파싱 로직이 없으므로, 프레임이 존재하면 소비 후 계속 반복하고
     * 더 이상 프레임이 없으면 Optional.empty()를 반환합니다.</p>
     *
     * @param buffer UNBOUND 누적 버퍼
     * @return 항상 Optional.empty()
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

    // TODO: HSMS S1F2 payload format 확정 후 secs2Decoder를 이용한 eqpId 파싱 로직 구현
}
