package com.nori.tc.business.core.workflow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link ActionResolutionPolicy} 단위 테스트입니다.
 */
class ActionResolutionPolicyTest {

    @Test
    void shouldResolvePluginAndMarkOverrideWhenPluginAndCoreAreBothPresent() {
        final BusinessWorkflowActionKey key = BusinessWorkflowActionKey.of(
                BusinessWorkflowActionMessageType.SOCKET,
                "SOCKET_ACT"
        );

        final BusinessWorkflowActionRegistry pluginRegistry = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(new PluginSocketExecutor(), BusinessWorkflowActionMessageType.SOCKET)
                .build();
        final BusinessWorkflowActionRegistry coreRegistry = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(new CoreSocketExecutor(), BusinessWorkflowActionMessageType.SOCKET)
                .build();

        final ActionResolutionTrace trace = ActionResolutionPolicy.pluginFirstFallbackCore().resolve(
                "EQP-RESOLVE-01",
                1001L,
                key,
                pluginRegistry,
                coreRegistry
        );

        Assertions.assertTrue(trace.isResolved(), "plugin/core 모두 있으면 반드시 해석되어야 합니다.");
        Assertions.assertEquals(ActionResolutionTrace.ResolutionSource.PLUGIN, trace.resolutionSource());
        Assertions.assertTrue(trace.isPluginOverride(), "plugin 우선 정책이면 override=true 이어야 합니다.");
        Assertions.assertFalse(trace.isCoreFallback());
    }

    @Test
    void shouldResolveCoreFallbackWhenPluginActionIsMissing() {
        final BusinessWorkflowActionKey key = BusinessWorkflowActionKey.of(
                BusinessWorkflowActionMessageType.SOCKET,
                "SOCKET_ACT"
        );

        final BusinessWorkflowActionRegistry pluginRegistry = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(new PluginOnlyActionExecutor(), BusinessWorkflowActionMessageType.SOCKET)
                .build();
        final BusinessWorkflowActionRegistry coreRegistry = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(new CoreSocketExecutor(), BusinessWorkflowActionMessageType.SOCKET)
                .build();

        final ActionResolutionTrace trace = ActionResolutionPolicy.pluginFirstFallbackCore().resolve(
                "EQP-RESOLVE-02",
                1002L,
                key,
                pluginRegistry,
                coreRegistry
        );

        Assertions.assertTrue(trace.isResolved(), "plugin에 key가 없으면 core fallback이 동작해야 합니다.");
        Assertions.assertEquals(ActionResolutionTrace.ResolutionSource.CORE, trace.resolutionSource());
        Assertions.assertTrue(trace.isCoreFallback(), "core fallback trace가 true 이어야 합니다.");
        Assertions.assertFalse(trace.isPluginOverride());
    }

    @Test
    void shouldReturnMissingWhenNoActionExistsInBothRegistries() {
        final BusinessWorkflowActionKey key = BusinessWorkflowActionKey.of(
                BusinessWorkflowActionMessageType.SOCKET,
                "UNKNOWN_ACTION"
        );

        final ActionResolutionTrace trace = ActionResolutionPolicy.pluginFirstFallbackCore().resolve(
                "EQP-RESOLVE-03",
                1003L,
                key,
                BusinessWorkflowActionRegistry.empty(),
                BusinessWorkflowActionRegistry.empty()
        );

        Assertions.assertFalse(trace.isResolved(), "plugin/core 모두 없으면 미해결이어야 합니다.");
        Assertions.assertEquals(ActionResolutionTrace.ResolutionSource.NONE, trace.resolutionSource());
        Assertions.assertFalse(trace.isPluginOverride());
        Assertions.assertFalse(trace.isCoreFallback());
    }

    /**
     * plugin/core 충돌 테스트에서 plugin 쪽 동일 액션을 제공합니다.
     */
    private static final class PluginSocketExecutor extends SocketActionExecutor {
        @TcAction("SOCKET_ACT")
        public void pluginSocketAct(final BusinessWorkflowActionContext context) {
            // no-op
        }
    }

    /**
     * plugin에는 존재하지만 fallback 대상 key와는 다른 액션을 제공합니다.
     */
    private static final class PluginOnlyActionExecutor extends SocketActionExecutor {
        @TcAction("PLUGIN_ONLY_ACTION")
        public void pluginOnlyAction(final BusinessWorkflowActionContext context) {
            // no-op
        }
    }

    /**
     * core fallback 테스트에서 core 쪽 동일 액션을 제공합니다.
     */
    private static final class CoreSocketExecutor extends SocketActionExecutor {
        @TcAction("SOCKET_ACT")
        public void coreSocketAct(final BusinessWorkflowActionContext context) {
            // no-op
        }
    }
}
