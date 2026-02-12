package com.nori.tc.comm.core.message;

import java.util.List;

/**
 * inbound 처리 결과(프레임 추출 + 파싱/변환)
 *
 * - parsedMessages : 파싱 완료된 메시지 목록(0개 이상)
 * - outboundFrames : 즉시 송신해야 할 raw frames(0개 이상)
 */
public record InboundProcessResult(
        List<ParsedMessage> parsedMessages,
        List<OutboundRawFrame> outboundFrames
) {
    public InboundProcessResult {
        if (parsedMessages == null) parsedMessages = List.of();
        if (outboundFrames == null) outboundFrames = List.of();
    }

    public static InboundProcessResult empty() {
        return new InboundProcessResult(List.of(), List.of());
    }
}
