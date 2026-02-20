package com.nori.tc.comm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

/**
 * Netty server/client configuration for the gateway.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.netty")
public class GatewayNettyProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayNettyProperties.class);

    /**
     * Boss (acceptor) thread count.
     */
    private Integer bossThreads;

    /**
     * Worker (IO) thread count.
     */
    private Integer workerThreads;

    /**
     * HSMS passive bind port.
     */
    private Integer hsmsBindPort;

    /**
     * SOCKET passive bind port.
     */
    private Integer socketBindPort;

    /**
     * Bind timeout for UNBOUND connections (seconds).
     */
    private Integer bindTimeoutSeconds;

    /**
     * Thread count for bind parsing executor (UNBOUND parsing).
     */
    private Integer bindExecutorThreads;

    /**
     * Max bytes kept in the UNBOUND buffer before closing.
     */
    private Integer unboundBufferMaxBytes;

    /**
     * Initial bytes for UNBOUND reassembly buffer.
     */
    private Integer unboundBufferInitialBytes;

    /**
     * Connect timeout for ACTIVE connections (ms).
     */
    private Integer connectTimeoutMillis;

    /**
     * Reconnect delay for ACTIVE connections (ms).
     */
    private Integer activeReconnectDelayMs;

    /**
     * Scheduler threads for ACTIVE reconnect handling.
     */
    private Integer reconnectSchedulerThreads;

    /**
     * 아웃바운드 연결의 연속 실패 허용 횟수입니다.
     *
     * <p>해당 횟수 이상 실패하면 자동 재연결을 중단하고 suppress 상태로 전환합니다.</p>
     */
    private Integer outboundMaxConsecutiveFailures;

    /**
     * Whether to send "CMD=INITIALIZE" on socket connect.
     */
    private Boolean socketSendInitializeOnConnect;

    /**
     * Socket initialize request (sent by gateway).
     */
    private String socketInitializeCommand;

    /**
     * Socket initialize response prefix (from client).
     */
    private String socketInitializeReplyPrefix;

    /**
     * Key used in initialize response (e.g. EQPID=XXXX).
     */
    private String socketEqpIdKey;

    
    /**
     * 게이트웨이 Netty 어댑터 입력/설정 유효성을 검증합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     */
    @PostConstruct
    public void validate() {
        if (bossThreads == null || bossThreads <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.boss-threads must be > 0");
        }
        if (workerThreads == null || workerThreads <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.worker-threads must be > 0");
        }
        if (hsmsBindPort == null || hsmsBindPort <= 0 || hsmsBindPort > 65535) {
            throw new IllegalStateException("tc.comm.gateway.netty.hsms-bind-port must be 1..65535");
        }
        if (socketBindPort == null || socketBindPort <= 0 || socketBindPort > 65535) {
            throw new IllegalStateException("tc.comm.gateway.netty.socket-bind-port must be 1..65535");
        }
        if (bindTimeoutSeconds == null || bindTimeoutSeconds <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.bind-timeout-seconds must be > 0");
        }
        if (bindExecutorThreads == null || bindExecutorThreads <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.bind-executor-threads must be > 0");
        }
        if (unboundBufferMaxBytes == null || unboundBufferMaxBytes <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.unbound-buffer-max-bytes must be > 0");
        }
        if (unboundBufferInitialBytes == null || unboundBufferInitialBytes <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.unbound-buffer-initial-bytes must be > 0");
        }
        if (unboundBufferInitialBytes > unboundBufferMaxBytes) {
            throw new IllegalStateException("tc.comm.gateway.netty.unbound-buffer-initial-bytes must be <= unbound-buffer-max-bytes");
        }
        if (connectTimeoutMillis == null || connectTimeoutMillis <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.connect-timeout-millis must be > 0");
        }
        if (activeReconnectDelayMs == null || activeReconnectDelayMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.active-reconnect-delay-ms must be > 0");
        }
        if (reconnectSchedulerThreads == null || reconnectSchedulerThreads <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.reconnect-scheduler-threads must be > 0");
        }
        if (outboundMaxConsecutiveFailures == null || outboundMaxConsecutiveFailures <= 0) {
            throw new IllegalStateException("tc.comm.gateway.netty.outbound-max-consecutive-failures must be > 0");
        }
        if (socketSendInitializeOnConnect == null) {
            throw new IllegalStateException("tc.comm.gateway.netty.socket-send-initialize-on-connect is required");
        }
        if (socketInitializeCommand == null || socketInitializeCommand.isBlank()) {
            throw new IllegalStateException("tc.comm.gateway.netty.socket-initialize-command is required");
        }
        if (socketInitializeReplyPrefix == null || socketInitializeReplyPrefix.isBlank()) {
            throw new IllegalStateException("tc.comm.gateway.netty.socket-initialize-reply-prefix is required");
        }
        if (socketEqpIdKey == null || socketEqpIdKey.isBlank()) {
            throw new IllegalStateException("tc.comm.gateway.netty.socket-eqp-id-key is required");
        }
        log.info("GatewayNettyProperties validated. bossThreads={}, workerThreads={}, hsmsBindPort={}, socketBindPort={}",
                bossThreads, workerThreads, hsmsBindPort, socketBindPort);
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getBossThreads() {
        return bossThreads;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param bossThreads 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public void setBossThreads(final int bossThreads) {
        this.bossThreads = bossThreads;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param workerThreads 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public void setWorkerThreads(final int workerThreads) {
        this.workerThreads = workerThreads;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getHsmsBindPort() {
        return hsmsBindPort;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param hsmsBindPort 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public void setHsmsBindPort(final int hsmsBindPort) {
        this.hsmsBindPort = hsmsBindPort;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getSocketBindPort() {
        return socketBindPort;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param socketBindPort 통신 채널/세션 정보
     */
    public void setSocketBindPort(final int socketBindPort) {
        this.socketBindPort = socketBindPort;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getBindTimeoutSeconds() {
        return bindTimeoutSeconds;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param bindTimeoutSeconds 시간 관련 설정 값
     */
    public void setBindTimeoutSeconds(final int bindTimeoutSeconds) {
        this.bindTimeoutSeconds = bindTimeoutSeconds;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getBindExecutorThreads() {
        return bindExecutorThreads;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param bindExecutorThreads 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public void setBindExecutorThreads(final int bindExecutorThreads) {
        this.bindExecutorThreads = bindExecutorThreads;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getUnboundBufferMaxBytes() {
        return unboundBufferMaxBytes;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param unboundBufferMaxBytes 처리할 원본 데이터
     */
    public void setUnboundBufferMaxBytes(final int unboundBufferMaxBytes) {
        this.unboundBufferMaxBytes = unboundBufferMaxBytes;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getUnboundBufferInitialBytes() {
        return unboundBufferInitialBytes;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param unboundBufferInitialBytes 처리할 원본 데이터
     */
    public void setUnboundBufferInitialBytes(final int unboundBufferInitialBytes) {
        this.unboundBufferInitialBytes = unboundBufferInitialBytes;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param connectTimeoutMillis 시간 관련 설정 값
     */
    public void setConnectTimeoutMillis(final int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getActiveReconnectDelayMs() {
        return activeReconnectDelayMs;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param activeReconnectDelayMs 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public void setActiveReconnectDelayMs(final int activeReconnectDelayMs) {
        this.activeReconnectDelayMs = activeReconnectDelayMs;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public int getReconnectSchedulerThreads() {
        return reconnectSchedulerThreads;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param reconnectSchedulerThreads 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public void setReconnectSchedulerThreads(final int reconnectSchedulerThreads) {
        this.reconnectSchedulerThreads = reconnectSchedulerThreads;
    }


    /**
     * 아웃바운드 연속 실패 허용 횟수를 조회합니다.
     *
     * @return 아웃바운드 연속 실패 허용 횟수
     */
    public int getOutboundMaxConsecutiveFailures() {
        return outboundMaxConsecutiveFailures;
    }


    /**
     * 아웃바운드 연속 실패 허용 횟수를 설정합니다.
     *
     * @param outboundMaxConsecutiveFailures 아웃바운드 연속 실패 허용 횟수
     */
    public void setOutboundMaxConsecutiveFailures(final int outboundMaxConsecutiveFailures) {
        this.outboundMaxConsecutiveFailures = outboundMaxConsecutiveFailures;
    }


    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean isSocketSendInitializeOnConnect() {
        return socketSendInitializeOnConnect;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param socketSendInitializeOnConnect 통신 채널/세션 정보
     */
    public void setSocketSendInitializeOnConnect(final boolean socketSendInitializeOnConnect) {
        this.socketSendInitializeOnConnect = socketSendInitializeOnConnect;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public String getSocketInitializeCommand() {
        return socketInitializeCommand;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param socketInitializeCommand 처리할 요청/명령 정보
     */
    public void setSocketInitializeCommand(final String socketInitializeCommand) {
        this.socketInitializeCommand = socketInitializeCommand;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public String getSocketInitializeReplyPrefix() {
        return socketInitializeReplyPrefix;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param socketInitializeReplyPrefix 통신 채널/세션 정보
     */
    public void setSocketInitializeReplyPrefix(final String socketInitializeReplyPrefix) {
        this.socketInitializeReplyPrefix = socketInitializeReplyPrefix;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public String getSocketEqpIdKey() {
        return socketEqpIdKey;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param socketEqpIdKey 대상 키 값
     */
    public void setSocketEqpIdKey(final String socketEqpIdKey) {
        this.socketEqpIdKey = socketEqpIdKey;
    }
}
