package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * Netty server/client configuration for the gateway.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.netty")
public class GatewayNettyProperties {

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
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(final int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(final int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getHsmsBindPort() {
        return hsmsBindPort;
    }

    public void setHsmsBindPort(final int hsmsBindPort) {
        this.hsmsBindPort = hsmsBindPort;
    }

    public int getSocketBindPort() {
        return socketBindPort;
    }

    public void setSocketBindPort(final int socketBindPort) {
        this.socketBindPort = socketBindPort;
    }

    public int getBindTimeoutSeconds() {
        return bindTimeoutSeconds;
    }

    public void setBindTimeoutSeconds(final int bindTimeoutSeconds) {
        this.bindTimeoutSeconds = bindTimeoutSeconds;
    }

    public int getBindExecutorThreads() {
        return bindExecutorThreads;
    }

    public void setBindExecutorThreads(final int bindExecutorThreads) {
        this.bindExecutorThreads = bindExecutorThreads;
    }

    public int getUnboundBufferMaxBytes() {
        return unboundBufferMaxBytes;
    }

    public void setUnboundBufferMaxBytes(final int unboundBufferMaxBytes) {
        this.unboundBufferMaxBytes = unboundBufferMaxBytes;
    }

    public int getUnboundBufferInitialBytes() {
        return unboundBufferInitialBytes;
    }

    public void setUnboundBufferInitialBytes(final int unboundBufferInitialBytes) {
        this.unboundBufferInitialBytes = unboundBufferInitialBytes;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(final int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getActiveReconnectDelayMs() {
        return activeReconnectDelayMs;
    }

    public void setActiveReconnectDelayMs(final int activeReconnectDelayMs) {
        this.activeReconnectDelayMs = activeReconnectDelayMs;
    }

    public int getReconnectSchedulerThreads() {
        return reconnectSchedulerThreads;
    }

    public void setReconnectSchedulerThreads(final int reconnectSchedulerThreads) {
        this.reconnectSchedulerThreads = reconnectSchedulerThreads;
    }

    public boolean isSocketSendInitializeOnConnect() {
        return socketSendInitializeOnConnect;
    }

    public void setSocketSendInitializeOnConnect(final boolean socketSendInitializeOnConnect) {
        this.socketSendInitializeOnConnect = socketSendInitializeOnConnect;
    }

    public String getSocketInitializeCommand() {
        return socketInitializeCommand;
    }

    public void setSocketInitializeCommand(final String socketInitializeCommand) {
        this.socketInitializeCommand = socketInitializeCommand;
    }

    public String getSocketInitializeReplyPrefix() {
        return socketInitializeReplyPrefix;
    }

    public void setSocketInitializeReplyPrefix(final String socketInitializeReplyPrefix) {
        this.socketInitializeReplyPrefix = socketInitializeReplyPrefix;
    }

    public String getSocketEqpIdKey() {
        return socketEqpIdKey;
    }

    public void setSocketEqpIdKey(final String socketEqpIdKey) {
        this.socketEqpIdKey = socketEqpIdKey;
    }
}
