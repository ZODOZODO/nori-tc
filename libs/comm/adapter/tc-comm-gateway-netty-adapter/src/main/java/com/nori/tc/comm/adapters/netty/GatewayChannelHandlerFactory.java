package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.application.ingress.GatewayIngressService;
import com.nori.tc.comm.gateway.config.props.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.props.GatewaySocketProperties;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Decoder;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogSampler;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * GatewayChannelHandler 생성 전용 팩토리입니다.
 *
 * <p>핵심 책임은 다음과 같습니다.</p>
 * <p>1) PASSIVE/ACTIVE 연결 타입에 맞는 핸들러 인스턴스를 생성합니다.</p>
 * <p>2) HSMS/SOCKET eqpId 추출기와 공통 의존 객체를 주입합니다.</p>
 */
@Component
public class GatewayChannelHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(GatewayChannelHandlerFactory.class);

    private final GatewayNettyProperties nettyProperties;
    private final GatewaySocketProperties socketProperties;
    private final GatewayIngressService processingService;
    private final EqpBindingService bindingService;
    private final BindAttemptExecutor bindExecutor;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;

    private final HsmsEqpIdExtractor hsmsExtractor;
    private final SocketEqpIdExtractor socketExtractor;

    /**
     * 핸들러 팩토리 의존 객체를 초기화합니다.
     *
     * @param nettyProperties Netty 설정
     * @param processingService 처리 서비스
     * @param bindingService 바인딩 서비스
     * @param bindExecutor 바인딩 시도 실행기
     * @param metrics 메트릭 수집기
     * @param logSampler 로그 샘플러
     * @param frameExtractor HSMS 프레임 추출기
     * @param secs2Decoder HSMS SECS-II 디코더
     * @param socketProperties SOCKET 설정
     * @param socketTypeRegistry SOCKET 타입 레지스트리
     */
    public GatewayChannelHandlerFactory(
            final GatewayNettyProperties nettyProperties,
            final GatewayIngressService processingService,
            final EqpBindingService bindingService,
            final BindAttemptExecutor bindExecutor,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler,
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder,
            final GatewaySocketProperties socketProperties,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.nettyProperties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.socketProperties = Objects.requireNonNull(socketProperties, "socketProperties is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService is null");
        this.bindExecutor = Objects.requireNonNull(bindExecutor, "bindExecutor is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");

        this.hsmsExtractor = new HsmsEqpIdExtractor(frameExtractor, secs2Decoder);
        this.socketExtractor = new SocketEqpIdExtractor(socketProperties, nettyProperties, socketTypeRegistry);
    }

    /**
     * 수신 경로(PASSIVE handler) 전용 핸들러를 생성합니다.
     *
     * @param interfaceType 인터페이스 타입
     * @return 생성된 채널 핸들러
     */
    public GatewayChannelHandler newPassiveHandler(
            final CommInterfaceType interfaceType,
            final String socketType
    ) {
        final String resolvedSocketType = resolveSocketTypeForChannel(interfaceType, socketType);
        if (log.isDebugEnabled()) {
            log.debug("Create PASSIVE handler. interfaceType={}, socketType={}", interfaceType, resolvedSocketType);
        }
        return new GatewayChannelHandler(
                interfaceType,
                null,
                resolvedSocketType,
                nettyProperties,
                processingService,
                bindingService,
                metrics,
                logSampler,
                hsmsExtractor,
                socketExtractor,
                bindExecutor
        );
    }

    /**
     * 발신 경로(ACTIVE handler) 전용 핸들러를 생성합니다.
     *
     * <p>eqpId가 이미 확정된 경로이므로 생성 로그도 eqp MDC 범위로 기록합니다.</p>
     *
     * @param interfaceType 인터페이스 타입
     * @param eqpId 설비 ID
     * @return 생성된 채널 핸들러
     */
    public GatewayChannelHandler newActiveHandler(
            final CommInterfaceType interfaceType,
            final String eqpId,
            final String socketType
    ) {
        final String resolvedSocketType = resolveSocketTypeForChannel(interfaceType, socketType);
        if (eqpId != null && !eqpId.isBlank()) {
            try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
                if (log.isDebugEnabled()) {
                    log.debug("Create ACTIVE handler. interfaceType={}, eqpId={}, socketType={}",
                            interfaceType,
                            eqpId,
                            resolvedSocketType);
                }
            }
        } else if (log.isDebugEnabled()) {
            log.debug("Create ACTIVE handler. interfaceType={}, eqpId={}, socketType={}",
                    interfaceType,
                    eqpId,
                    resolvedSocketType);
        }

        return new GatewayChannelHandler(
                interfaceType,
                eqpId,
                resolvedSocketType,
                nettyProperties,
                processingService,
                bindingService,
                metrics,
                logSampler,
                hsmsExtractor,
                socketExtractor,
                bindExecutor
        );
    }

    /**
     * 채널 생성 시 사용할 socketType을 해석합니다.
     *
     * <p>HSMS 채널은 socketType이 의미 없으므로 null을 반환하고,
     * SOCKET 채널은 전달값 우선 -> 기본값 fallback 순서로 확정합니다.</p>
     *
     * @param interfaceType 채널 인터페이스 타입
     * @param requestedSocketType 외부에서 전달한 socketType(설비/리스너 기준)
     * @return 채널에 주입할 socketType (HSMS면 null)
     */
    private String resolveSocketTypeForChannel(
            final CommInterfaceType interfaceType,
            final String requestedSocketType
    ) {
        if (interfaceType != CommInterfaceType.SOCKET) {
            return null;
        }

        final String normalized = normalizeText(requestedSocketType);
        if (normalized != null) {
            return normalized;
        }

        final String fallback = normalizeText(socketProperties.getDefaultSocketType());
        if (fallback == null) {
            throw new IllegalStateException("Default SOCKET socketType is not configured");
        }
        if (log.isDebugEnabled()) {
            log.debug("SOCKET handler 생성 시 socketType 미지정으로 기본값 fallback 적용. socketType={}", fallback);
        }
        return fallback;
    }

    /**
     * 문자열 정규화 유틸리티입니다.
     *
     * @param text 입력 문자열
     * @return trim 결과가 비어 있지 않으면 해당 문자열, 아니면 null
     */
    private String normalizeText(final String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
