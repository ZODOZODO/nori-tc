package com.nori.tc.business.core.workflow;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.action.TcAction;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionKey;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.action.AbstractSocketActionExecutor;
import com.nori.tc.business.core.workflow.internal.resolution.ActionResolutionPolicy;
import com.nori.tc.business.core.workflow.internal.resolution.ActionResolutionTrace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link ActionResolutionPolicy} ?⑥쐞 ?뚯뒪?몄엯?덈떎.
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

        Assertions.assertTrue(trace.isResolved(), "plugin/core 紐⑤몢 ?덉쑝硫?諛섎뱶???댁꽍?섏뼱???⑸땲??");
        Assertions.assertEquals(ActionResolutionTrace.ResolutionSource.PLUGIN, trace.resolutionSource());
        Assertions.assertTrue(trace.isPluginOverride(), "plugin ?곗꽑 ?뺤콉?대㈃ override=true ?댁뼱???⑸땲??");
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

        Assertions.assertTrue(trace.isResolved(), "plugin??key媛 ?놁쑝硫?core fallback???숈옉?댁빞 ?⑸땲??");
        Assertions.assertEquals(ActionResolutionTrace.ResolutionSource.CORE, trace.resolutionSource());
        Assertions.assertTrue(trace.isCoreFallback(), "core fallback trace媛 true ?댁뼱???⑸땲??");
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

        Assertions.assertFalse(trace.isResolved(), "plugin/core 紐⑤몢 ?놁쑝硫?誘명빐寃곗씠?댁빞 ?⑸땲??");
        Assertions.assertEquals(ActionResolutionTrace.ResolutionSource.NONE, trace.resolutionSource());
        Assertions.assertFalse(trace.isPluginOverride());
        Assertions.assertFalse(trace.isCoreFallback());
    }

    /**
     * plugin/core 異⑸룎 ?뚯뒪?몄뿉??plugin 履??숈씪 ?≪뀡???쒓났?⑸땲??
     */
    private static final class PluginSocketExecutor extends AbstractSocketActionExecutor {
        /**
         * pluginSocketAct 湲곕뒫???섑뻾?⑸땲??
         *
         * @param context ?낅젰 媛?         */

        @TcAction("SOCKET_ACT")
        public void pluginSocketAct(final BusinessWorkflowActionContext context) {
            // no-op
        }
    }

    /**
     * plugin?먮뒗 議댁옱?섏?留?fallback ???key????ㅻⅨ ?≪뀡???쒓났?⑸땲??
     */
    private static final class PluginOnlyActionExecutor extends AbstractSocketActionExecutor {
        /**
         * pluginOnlyAction 湲곕뒫???섑뻾?⑸땲??
         *
         * @param context ?낅젰 媛?         */

        @TcAction("PLUGIN_ONLY_ACTION")
        public void pluginOnlyAction(final BusinessWorkflowActionContext context) {
            // no-op
        }
    }

    /**
     * core fallback ?뚯뒪?몄뿉??core 履??숈씪 ?≪뀡???쒓났?⑸땲??
     */
    private static final class CoreSocketExecutor extends AbstractSocketActionExecutor {
        /**
         * coreSocketAct 湲곕뒫???섑뻾?⑸땲??
         *
         * @param context ?낅젰 媛?         */

        @TcAction("SOCKET_ACT")
        public void coreSocketAct(final BusinessWorkflowActionContext context) {
            // no-op
        }
    }
}

