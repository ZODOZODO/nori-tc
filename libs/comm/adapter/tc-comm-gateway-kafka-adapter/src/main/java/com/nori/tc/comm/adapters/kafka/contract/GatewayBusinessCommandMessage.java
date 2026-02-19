package com.nori.tc.comm.adapters.kafka.contract;

/**
 * Business -> Gateway 紐낅졊 ?섏떊??Kafka 硫붿떆吏 怨꾩빟?낅땲??
 *
 * <p>?붽뎄?ы빆 湲곗?:
 * 1) tc.eqp.events 諛쒗뻾 援ъ“(metadata + data)? ?숈씪??JSON ?뺥깭瑜??ъ슜?⑸땲??
 * 2) eventType 媛믪? ?쇱슦???ㅺ? ?꾨땲???댁쁺/異붿쟻 濡쒓렇 ?⑸룄濡쒕쭔 ?ъ슜?⑸땲??
 * 3) SOCKET/HSMS瑜??섎굹??怨꾩빟?쇰줈 ?섏슜?섎릺, ?꾩옱 援ы쁽 ?④퀎?먯꽌??SOCKET 泥섎━留??쒖꽦?뷀빀?덈떎.</p>
 *
 * <p>二쇱쓽:
 * - ?몃? ?쒖뒪???낅젰???덉젙?곸쑝濡??섏슜?섍린 ?꾪빐 record ?앹꽦?먯뿉??媛뺥븳 ?덉쇅 寃利앹? ?먯? ?딆뒿?덈떎.
 * - ?ㅼ젣 ?꾩닔媛?寃利??ㅻ쪟 遺꾨쪟???붿뒪?⑥쿂 怨꾩링?먯꽌 ?섑뻾?⑸땲??</p>
 */
public record GatewayBusinessCommandMessage(
        GatewayBusinessCommandMetadata metadata,
        GatewayBusinessCommandData data
) {

    /**
     * 怨듯넻 硫뷀??곗씠??釉붾줉?낅땲??
     *
     * <p>?덉떆:
     * - eventType: CHECK_REPLY, S6F11 ??     * - timestamp: ISO-8601 臾몄옄??     * - source   : 諛쒗뻾 ?쒖뒪???앸퀎??     * - traceId  : 遺꾩궛 異붿쟻 ?앸퀎??/p>
     */
    public record GatewayBusinessCommandMetadata(
            String eventType,
            String timestamp,
            String source,
            String traceId
    ) {
    }

    /**
     * 怨듯넻 ?곗씠??釉붾줉?낅땲??
     *
     * <p>SOCKET/HSMS瑜??④퍡 ?쒗쁽?섍린 ?꾪빐 ?꾨뱶瑜??듯빀?덉뒿?덈떎.
     * - eqpId/interfaceType/rawMessage: SOCKET 泥섎━ ???듭떖 ?낅젰
     * - transactionId/secs2         : HSMS ?뺤옣 ?낅젰(TODO ?④퀎)</p>
     */
    public record GatewayBusinessCommandData(
            String transactionId,
            String eqpId,
            String interfaceType,
            GatewayBusinessCommandSecs2 secs2,
            String rawMessage
    ) {
    }

    /**
     * HSMS SECS-II ?몃? 釉붾줉?낅땲??
     *
     * <p>?꾩옱 援ы쁽 踰붿쐞?먯꽌??HSMS ?≪떊???쒖꽦?뷀븯吏 ?딆쑝誘濡?
     * ?섏떊/濡쒓렇/?ν썑 ?뺤옣???꾪븳 怨꾩빟 蹂댁〈 紐⑹쟻??援ъ“?낅땲??</p>
     */
    public record GatewayBusinessCommandSecs2(
            String systemBytes,
            String eventId,
            String rawBody
    ) {
    }
}

