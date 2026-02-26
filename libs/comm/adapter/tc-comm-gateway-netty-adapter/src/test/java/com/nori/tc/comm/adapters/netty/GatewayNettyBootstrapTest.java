package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.db.ConnectionMode;
import com.nori.tc.comm.gateway.equipment.port.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.config.props.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.props.GatewaySocketProperties;
import com.nori.tc.comm.gateway.context.service.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.gateway.lifecycle.service.EquipmentLifecycleStateMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayNettyBootstrap}의 DB/컨텍스트 기반 런타임 동작을 검증하는 테스트입니다.
 *
 * <p>검증 범위:</p>
 * <p>1) 게이트웨이 기준 PASSIVE 공유 listener(interface + port) 생성/공유/정지</p>
 * <p>2) 게이트웨이 기준 ACTIVE 런타임 시작 시 아웃바운드 연결 시도</p>
 *
 * <p>주의:</p>
 * <p>- Netty event loop 및 실제 localhost 소켓 바인딩을 사용합니다.</p>
 * <p>- 테스트 간 포트 충돌 방지를 위해 매번 사용 가능한 포트를 동적으로 할당합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class GatewayNettyBootstrapTest {

    /**
     * 장비 정보 조회 포트 모킹 객체입니다.
     */
    @Mock
    private EquipmentInfoProvider equipmentInfoProvider;

    /**
     * 컨텍스트 레지스트리 모킹 객체입니다.
     *
     * <p>부팅 시 컨텍스트 기반 런타임 자동 기동을 비활성화하기 위해 빈 스냅샷을 반환하도록 설정합니다.</p>
     */
    @Mock
    private EquipmentContextRegistry equipmentContextRegistry;

    /**
     * 채널 핸들러 팩토리 모킹 객체입니다.
     */
    @Mock
    private GatewayChannelHandlerFactory handlerFactory;

    /**
     * 샤드 소유권 판단 모킹 객체입니다.
     */
    @Mock
    private KafkaShardOwnership shardOwnership;

    /**
     * lifecycle 상태머신 모킹 객체입니다.
     */
    @Mock
    private EquipmentLifecycleStateMachine lifecycleStateMachine;

    /**
     * 테스트 중 생성된 Netty 부트스트랩을 종료하기 위한 참조입니다.
     */
    private GatewayNettyBootstrap bootstrapUnderTest;

    /**
     * 테스트 종료 후 Netty 리소스를 정리합니다.
     *
     * <p>실패 케이스에서도 event loop/thread 가 남지 않도록 finally 성격으로 사용합니다.</p>
     */
    @AfterEach
    void tearDown() {
        if (bootstrapUnderTest != null && bootstrapUnderTest.isRunning()) {
            bootstrapUnderTest.stop();
        }
    }

    /**
     * 동일한 interface + port 를 가진 PASSIVE 장비 2대는 공유 listener 1개를 사용해야 함을 검증합니다.
     *
     * <p>검증 포인트:</p>
     * <p>1) 첫 번째 PASSIVE 장비 시작 시 listener 생성</p>
     * <p>2) 두 번째 PASSIVE 장비 시작 시 listener 재사용 (채널 수 1 유지)</p>
     * <p>3) 첫 번째 장비 정지 시 listener 유지 (멤버 1명 남음)</p>
     * <p>4) 마지막 장비 정지 시 listener 종료</p>
     */
    @Test
    void passiveEquipmentsWithSameInterfaceAndPortShouldShareSingleListener() throws Exception {
        final int sharedPort = findAvailablePort();

        when(equipmentContextRegistry.snapshot()).thenReturn(java.util.List.of());
        when(shardOwnership.isOwnedPartition(anyInt())).thenReturn(true);

        final GatewayEquipmentInfo eqpA = new GatewayEquipmentInfo(
                1L,
                "EQP_A",
                CommInterfaceType.HSMS,
                null,
                10,
                "127.0.0.1",
                sharedPort,
                101L,
                ConnectionMode.PASSIVE,
                0,
                true
        );
        final GatewayEquipmentInfo eqpB = new GatewayEquipmentInfo(
                2L,
                "EQP_B",
                CommInterfaceType.HSMS,
                null,
                11,
                "127.0.0.1",
                sharedPort,
                102L,
                ConnectionMode.PASSIVE,
                1,
                true
        );

        when(equipmentInfoProvider.findById("EQP_A")).thenReturn(Optional.of(eqpA));
        when(equipmentInfoProvider.findById("EQP_B")).thenReturn(Optional.of(eqpB));

        bootstrapUnderTest = newBootstrapAndStart();

        bootstrapUnderTest.startRuntimeIfPossible("EQP_A");
        bootstrapUnderTest.startRuntimeIfPossible("EQP_B");

        final Map<?, ?> listenerChannelMapAfterStart = getPrivateMapField(bootstrapUnderTest, "passiveListenerChannels");
        final Map<?, ?> listenerMembersMapAfterStart = getPrivateMapField(bootstrapUnderTest, "passiveListenerMembers");

        Assertions.assertEquals(1, listenerChannelMapAfterStart.size(),
                "동일 interface+port PASSIVE 장비는 listener 1개만 생성되어야 합니다.");
        Assertions.assertEquals(1, listenerMembersMapAfterStart.size(),
                "공유 listener key 는 1개만 유지되어야 합니다.");
        Assertions.assertEquals(2, firstMemberSetSize(listenerMembersMapAfterStart),
                "공유 listener 멤버십에는 PASSIVE 장비 2대가 등록되어야 합니다.");

        bootstrapUnderTest.stopRuntimeIfPossible("EQP_A");

        final Map<?, ?> listenerChannelMapAfterFirstStop = getPrivateMapField(bootstrapUnderTest, "passiveListenerChannels");
        final Map<?, ?> listenerMembersMapAfterFirstStop = getPrivateMapField(bootstrapUnderTest, "passiveListenerMembers");

        Assertions.assertEquals(1, listenerChannelMapAfterFirstStop.size(),
                "공유 listener 멤버가 남아 있으면 listener 는 유지되어야 합니다.");
        Assertions.assertEquals(1, firstMemberSetSize(listenerMembersMapAfterFirstStop),
                "첫 번째 장비 정지 후 멤버십은 1명이어야 합니다.");

        bootstrapUnderTest.stopRuntimeIfPossible("EQP_B");

        final Map<?, ?> listenerChannelMapAfterLastStop = getPrivateMapField(bootstrapUnderTest, "passiveListenerChannels");
        final Map<?, ?> listenerMembersMapAfterLastStop = getPrivateMapField(bootstrapUnderTest, "passiveListenerMembers");

        Assertions.assertEquals(0, listenerChannelMapAfterLastStop.size(),
                "마지막 PASSIVE 장비 정지 후 공유 listener 는 종료되어야 합니다.");
        Assertions.assertEquals(0, listenerMembersMapAfterLastStop.size(),
                "마지막 PASSIVE 장비 정지 후 멤버십 맵도 비워져야 합니다.");
    }

    /**
     * PASSIVE SOCKET 설비가 동일 포트를 공유하면서 socketType이 다르면 정책 위반으로 두 번째 시작이 거부되는지 검증합니다.
     *
     * <p>검증 포인트:</p>
     * <p>1) 첫 번째 SOCKET PASSIVE 설비는 정상적으로 listener를 생성합니다.</p>
     * <p>2) 같은 포트/다른 socketType 설비 시작 시 fail-fast 처리되어 listener 멤버십에 합류하지 못합니다.</p>
     * <p>3) lifecycleStateMachine에 시작 실패 콜백이 전달됩니다.</p>
     */
    @Test
    void activeSocketEquipmentsWithSamePortButDifferentSocketTypeShouldBeRejected() throws Exception {
        final int sharedPort = findAvailablePort();

        when(equipmentContextRegistry.snapshot()).thenReturn(java.util.List.of());
        when(shardOwnership.isOwnedPartition(anyInt())).thenReturn(true);

        final GatewayEquipmentInfo socketEqpA = new GatewayEquipmentInfo(
                101L,
                "SOCKET_A",
                CommInterfaceType.SOCKET,
                "LINE_DELIMITED",
                null,
                "127.0.0.1",
                sharedPort,
                201L,
                ConnectionMode.PASSIVE,
                2,
                true
        );
        final GatewayEquipmentInfo socketEqpB = new GatewayEquipmentInfo(
                102L,
                "SOCKET_B",
                CommInterfaceType.SOCKET,
                "REGEX_DELIMITED",
                null,
                "127.0.0.1",
                sharedPort,
                202L,
                ConnectionMode.PASSIVE,
                3,
                true
        );

        when(equipmentInfoProvider.findById("SOCKET_A")).thenReturn(Optional.of(socketEqpA));
        when(equipmentInfoProvider.findById("SOCKET_B")).thenReturn(Optional.of(socketEqpB));

        bootstrapUnderTest = newBootstrapAndStart();

        bootstrapUnderTest.startRuntimeIfPossible("SOCKET_A");
        bootstrapUnderTest.startRuntimeIfPossible("SOCKET_B");

        final Map<?, ?> listenerChannelMap = getPrivateMapField(bootstrapUnderTest, "passiveListenerChannels");
        final Map<?, ?> listenerMembersMap = getPrivateMapField(bootstrapUnderTest, "passiveListenerMembers");
        final Map<?, ?> listenerSocketTypeConstraintMap = getPrivateMapField(bootstrapUnderTest, "passiveListenerSocketTypeConstraints");

        Assertions.assertEquals(1, listenerChannelMap.size(),
                "첫 번째 SOCKET PASSIVE 설비가 생성한 listener 1개만 유지되어야 합니다.");
        Assertions.assertEquals(1, listenerMembersMap.size(),
                "정책 위반 설비는 멤버십에 합류하지 못하므로 listener key는 1개여야 합니다.");
        Assertions.assertEquals(1, firstMemberSetSize(listenerMembersMap),
                "동일 포트/다른 socketType 설비는 거부되어 멤버 수는 1명이어야 합니다.");
        Assertions.assertEquals(1, listenerSocketTypeConstraintMap.size(),
                "PASSIVE SOCKET listener socketType 제약값은 최초 설비 기준 1개만 유지되어야 합니다.");
        Assertions.assertTrue(listenerSocketTypeConstraintMap.values().contains("LINE_DELIMITED"),
                "listener socketType 제약값은 첫 번째 설비의 socketType이어야 합니다.");

        verify(lifecycleStateMachine).onStartFailedIfPending("SOCKET_B", "SYSTEM", "PASSIVE_LISTENER_START_FAILED");
    }

    /**
     * ACTIVE 장비 런타임 시작 시 아웃바운드 연결을 실제로 시도하는지 검증합니다.
     *
     * <p>테스트 방식:</p>
     * <p>1) localhost 임시 서버를 띄웁니다.</p>
     * <p>2) ACTIVE 장비 런타임 시작(startRuntimeIfPossible)을 호출합니다.</p>
     * <p>3) 서버 측 accept 가 발생하면 아웃바운드 연결 시도가 수행된 것으로 판단합니다.</p>
     */
    @Test
    void activeRuntimeStartShouldAttemptOutboundConnection() throws Exception {
        when(equipmentContextRegistry.snapshot()).thenReturn(java.util.List.of());
        when(shardOwnership.isOwnedPartition(anyInt())).thenReturn(true);

        /**
         * ACTIVE(게이트웨이 기준) 아웃바운드 연결 성공 시 Netty pipeline 에 ACTIVE handler 가 필요합니다.
         * GatewayChannelHandler 는 final 클래스이므로 inline mock maker(mockito-inline)로 mock 생성합니다.
         */
        when(handlerFactory.newActiveHandler(any(CommInterfaceType.class), anyString(), any()))
                .thenReturn(mock(GatewayChannelHandler.class));

        bootstrapUnderTest = newBootstrapAndStart();

        final CountDownLatch acceptedLatch = new CountDownLatch(1);
        final AtomicReference<Socket> acceptedSocketRef = new AtomicReference<>();
        final AtomicReference<Throwable> acceptErrorRef = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            serverSocket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5));
            final int remotePort = serverSocket.getLocalPort();

            final Thread acceptThread = new Thread(() -> {
                try {
                    final Socket accepted = serverSocket.accept();
                    acceptedSocketRef.set(accepted);
                    acceptedLatch.countDown();
                } catch (SocketTimeoutException timeout) {
                    acceptErrorRef.set(timeout);
                } catch (IOException ioException) {
                    acceptErrorRef.set(ioException);
                }
            }, "gateway-netty-bootstrap-test-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            final GatewayEquipmentInfo activeEqp = new GatewayEquipmentInfo(
                    10L,
                    "ACTIVE_EQP",
                    CommInterfaceType.HSMS,
                    null,
                    22,
                    "127.0.0.1",
                    remotePort,
                    110L,
                    ConnectionMode.ACTIVE,
                    4,
                    true
            );
            when(equipmentInfoProvider.findById("ACTIVE_EQP")).thenReturn(Optional.of(activeEqp));

            bootstrapUnderTest.startRuntimeIfPossible("ACTIVE_EQP");

            final boolean accepted = acceptedLatch.await(5, TimeUnit.SECONDS);
            Assertions.assertTrue(accepted,
                    "ACTIVE 런타임 시작 시 localhost 테스트 서버로 아웃바운드 연결이 수립되어야 합니다.");
            Assertions.assertNull(acceptErrorRef.get(),
                    "ACTIVE(outbound) 연결 검증 중 서버 accept 단계에서 예외가 발생하면 안 됩니다.");

            /**
             * 이후 close 이벤트가 발생하더라도 재연결이 반복되지 않도록 ACTIVE 런타임을 먼저 정지합니다.
             */
            bootstrapUnderTest.stopRuntimeIfPossible("ACTIVE_EQP");

            final Socket acceptedSocket = acceptedSocketRef.get();
            if (acceptedSocket != null) {
                acceptedSocket.close();
            }

            acceptThread.join(TimeUnit.SECONDS.toMillis(2));
        }
    }

    /**
     * 테스트용 {@link GatewayNettyBootstrap} 인스턴스를 생성하고 시작합니다.
     *
     * <p>부팅 시 자동 런타임 기동 간섭을 막기 위해 EquipmentContextRegistry 는 빈 스냅샷을 반환하도록
     * 각 테스트에서 사전에 스텁합니다.</p>
     *
     * @return 시작 완료된 GatewayNettyBootstrap
     */
    private GatewayNettyBootstrap newBootstrapAndStart() {
        final GatewayNettyProperties properties = new GatewayNettyProperties();
        configureTestNettyProperties(properties);
        final GatewaySocketProperties socketProperties = new GatewaySocketProperties();
        socketProperties.setDefaultSocketType("LINE_DELIMITED");

        final GatewayNettyBootstrap bootstrap = new GatewayNettyBootstrap(
                properties,
                socketProperties,
                equipmentInfoProvider,
                equipmentContextRegistry,
                handlerFactory,
                shardOwnership,
                lifecycleStateMachine
        );
        bootstrap.start();
        return bootstrap;
    }

    /**
     * 테스트 실행에 필요한 최소 Netty 설정값을 구성합니다.
     *
     * <p>{@link GatewayNettyBootstrap} 및 아웃바운드 연결 코드에서 참조하는 항목을 중심으로 설정하며,
     * 값은 테스트 시간 단축/안정성을 고려한 작은 값으로 지정합니다.</p>
     *
     * @param properties 설정 대상 객체
     */
    private void configureTestNettyProperties(final GatewayNettyProperties properties) {
        properties.setBossThreads(1);
        properties.setWorkerThreads(1);
        properties.setBindTimeoutSeconds(2);
        properties.setBindExecutorThreads(1);
        properties.setUnboundBufferMaxBytes(1024 * 1024);
        properties.setUnboundBufferInitialBytes(1024);
        properties.setConnectTimeoutMillis(2000);
        properties.setActiveReconnectDelayMs(300);
        properties.setReconnectSchedulerThreads(1);
        properties.setOutboundMaxConsecutiveFailures(3);
        properties.setSocketSendInitializeOnConnect(false);
        properties.setSocketInitializeCommand("CMD=INITIALIZE");
        properties.setSocketInitializeReplyPrefix("CMD=INITIALIZE_REP");
        properties.setSocketEqpIdKey("EQPID");
    }

    /**
     * 공유 listener 멤버십 맵의 첫 번째 멤버 Set 크기를 반환합니다.
     *
     * @param membersMap passiveListenerMembers 리플렉션 조회 결과
     * @return 첫 번째 멤버십 Set 크기
     */
    private int firstMemberSetSize(final Map<?, ?> membersMap) {
        if (membersMap.isEmpty()) {
            return 0;
        }

        final Object firstValue = membersMap.values().iterator().next();
        if (firstValue instanceof Set<?> memberSet) {
            return memberSet.size();
        }
        if (firstValue instanceof Collection<?> collection) {
            return collection.size();
        }
        throw new IllegalStateException("Unexpected passiveListenerMembers value type: " + firstValue);
    }

    /**
     * 사용 가능한 로컬 포트를 동적으로 할당받아 반환합니다.
     *
     * <p>ACTIVE 공유 listener 바인드 테스트에서 포트 충돌을 줄이기 위해 사용합니다.</p>
     *
     * @return 사용 가능한 포트 번호
     * @throws IOException 소켓 생성 실패 시 예외
     */
    /**
     * {@link GatewayNettyBootstrap} 내부 private Map 필드를 reflection 으로 조회합니다.
     *
     * <p>본 테스트는 PASSIVE listener 관련 내부 맵 상태를 직접 검증해야 하므로
     * production 코드에 테스트 전용 getter 를 추가하지 않고 reflection 을 사용합니다.</p>
     *
     * @param target private 필드를 조회할 {@link GatewayNettyBootstrap} 인스턴스
     * @param fieldName 조회할 private 필드명
     * @return reflection 으로 조회한 Map 필드 값
     * @throws ReflectiveOperationException reflection 접근 실패 시 예외
     */
    private Map<?, ?> getPrivateMapField(
            final GatewayNettyBootstrap target,
            final String fieldName
    ) throws ReflectiveOperationException {
        if (target == null) {
            throw new IllegalArgumentException("target is null");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName is blank");
        }

        // 테스트 대상 구현 클래스의 private 필드를 직접 조회합니다.
        final Field field = GatewayNettyBootstrap.class.getDeclaredField(fieldName);
        field.setAccessible(true);

        // 런타임 타입을 검증하여 구현 변경 시 실패 원인이 명확하게 보이도록 처리합니다.
        final Object fieldValue = field.get(target);
        if (fieldValue == null) {
            throw new IllegalStateException("Private field is null: " + fieldName);
        }
        if (!(fieldValue instanceof Map<?, ?> mapFieldValue)) {
            throw new IllegalStateException(
                    "Private field is not a Map. fieldName=" + fieldName
                            + ", actualType=" + fieldValue.getClass().getName()
            );
        }
        return mapFieldValue;
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }
}
