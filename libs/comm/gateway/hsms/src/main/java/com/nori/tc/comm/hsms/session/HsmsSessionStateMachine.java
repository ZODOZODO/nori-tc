package com.nori.tc.comm.hsms.session;

import com.nori.tc.comm.hsms.config.HsmsSessionConfig;
import com.nori.tc.comm.hsms.frame.HsmsControlFrameFactory;
import com.nori.tc.comm.hsms.frame.HsmsFrame;
import com.nori.tc.comm.hsms.frame.HsmsHeader;
import com.nori.tc.comm.hsms.frame.HsmsSType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * HSMS 세션 상태 머신(단순화)
 *
 * 처리 목표(실무 핵심)
 * - SELECT_REQ 수신 시 SELECTED로 전환 + SELECT_RSP 송신
 * - LINKTEST_REQ 수신 시 LINKTEST_RSP 송신
 * - DESELECT_REQ 수신 시 NOT_SELECTED로 전환 + DESELECT_RSP 송신
 * - SEPARATE_REQ 수신 시 NOT_SELECTED로 전환(상위 레이어에서 재연결/격리 판단)
 *
 * tick(now)
 * - linktestEnabled인 경우, 일정 주기로 LINKTEST_REQ를 생성할 수 있도록 outbound 프레임을 반환
 *
 * 주의(중요)
 * - T3/T6 등 트랜잭션 타임아웃을 완전 구현하지 않습니다(뼈대).
 * - 향후 필요 시, "요청-응답 상관관계" 및 "pending control"을 확장할 수 있게 구조를 단순하게 유지합니다.
 */
public final class HsmsSessionStateMachine {

    private final HsmsSessionConfig config;

    private HsmsSessionState state = HsmsSessionState.NOT_SELECTED;

    private long lastRxAtMs = 0;
    private long lastTxAtMs = 0;

    private long lastLinktestTxAtMs = 0;

    public HsmsSessionStateMachine(final HsmsSessionConfig config) {
        this.config = Objects.requireNonNull(config, "config is null");
    }

    public HsmsSessionConfig config() {
        return config;
    }

    public HsmsSessionState state() {
        return state;
    }

    /**
     * inbound frame 처리
     *
     * @param frame inbound HSMS frame
     * @param nowMs 현재 시각(epoch millis)
     */
    public SessionHandleResult onInboundFrame(final HsmsFrame frame, final long nowMs) {
        Objects.requireNonNull(frame, "frame is null");

        lastRxAtMs = nowMs;

        final HsmsHeader h = frame.header();
        final HsmsSType sType = h.sType();

        // Control 프레임 처리
        if (sType == HsmsSType.SELECT_REQ) {
            // 상대가 select를 요구 → 응답 + selected 전환
            state = HsmsSessionState.SELECTED;

            final HsmsFrame rsp = HsmsControlFrameFactory.selectRsp(config.deviceId(), h.systemBytes());
            return new SessionHandleResult(List.of(rsp), false);
        }

        if (sType == HsmsSType.SELECT_RSP) {
            // 우리가 select 요청을 보낸 상태라면 selected로 전환(단순 처리)
            state = HsmsSessionState.SELECTED;
            return SessionHandleResult.denyData();
        }

        if (sType == HsmsSType.DESELECT_REQ) {
            state = HsmsSessionState.NOT_SELECTED;
            final HsmsFrame rsp = HsmsControlFrameFactory.deselectRsp(config.deviceId(), h.systemBytes());
            return new SessionHandleResult(List.of(rsp), false);
        }

        if (sType == HsmsSType.DESELECT_RSP) {
            state = HsmsSessionState.NOT_SELECTED;
            return SessionHandleResult.denyData();
        }

        if (sType == HsmsSType.LINKTEST_REQ) {
            final HsmsFrame rsp = HsmsControlFrameFactory.linktestRsp(config.deviceId(), h.systemBytes());
            return new SessionHandleResult(List.of(rsp), false);
        }

        if (sType == HsmsSType.LINKTEST_RSP) {
            return SessionHandleResult.denyData();
        }

        if (sType == HsmsSType.SEPARATE_REQ) {
            // 세션 분리 요청 → selected 해제
            state = HsmsSessionState.NOT_SELECTED;
            return SessionHandleResult.denyData();
        }

        // DATA 프레임 처리 여부 판단
        if (sType == HsmsSType.DATA) {
            if (config.requireSelectBeforeData() && state != HsmsSessionState.SELECTED) {
                // Selected 상태 강제(안전 우선)
                return SessionHandleResult.denyData();
            }
            return SessionHandleResult.allowData();
        }

        // 여기까지 왔다면 알 수 없는 control(또는 REJECT 등)
        // - REJECT는 운영에서 의미가 있지만, 이 뼈대에서는 “data 처리 불가”로만 처리합니다.
        return SessionHandleResult.denyData();
    }

    /**
     * 주기 tick 처리
     *
     * - linktestEnabled이면 linktestIntervalMs마다 LINKTEST_REQ 생성
     *
     * @param nowMs 현재 시각(epoch millis)
     * @param systemBytesGenerator systemBytes 생성 함수(상위 레이어에서 주입)
     * @return 생성된 control frames (없으면 빈 리스트)
     */
    public List<HsmsFrame> tick(final long nowMs, final java.util.function.IntSupplier systemBytesGenerator) {
        final List<HsmsFrame> out = new ArrayList<>();

        if (!config.linktestEnabled()) return out;
        if (state != HsmsSessionState.SELECTED) return out;

        if (lastLinktestTxAtMs == 0) {
            lastLinktestTxAtMs = nowMs;
            return out;
        }

        final long elapsed = nowMs - lastLinktestTxAtMs;
        if (elapsed >= config.linktestIntervalMs()) {
            final int sysBytes = systemBytesGenerator.getAsInt();
            out.add(HsmsControlFrameFactory.linktestReq(config.deviceId(), sysBytes));
            lastLinktestTxAtMs = nowMs;
            lastTxAtMs = nowMs;
        }

        return out;
    }

    /**
     * SELECT_REQ를 능동적으로 보내고 싶을 때 사용할 수 있는 헬퍼(옵션)
     *
     * - 실제로 언제 보내는지는 app 레이어(연결 직후 등)에서 결정합니다.
     */
    public HsmsFrame createSelectReq(final int systemBytes) {
        lastTxAtMs = System.currentTimeMillis();
        return HsmsControlFrameFactory.selectReq(config.deviceId(), systemBytes);
    }

    public long lastRxAtMs() {
        return lastRxAtMs;
    }

    public long lastTxAtMs() {
        return lastTxAtMs;
    }
}
