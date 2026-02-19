package com.nori.tc.business.domain.modelcache;

import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.common.model.VariableIdType;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelVariableId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * modelKey 단위 런타임 컨텍스트입니다.
 *
 * <p>핫패스에서 DB 조회를 피하기 위해 workflow/message/variable 인덱스를
 * 모두 불변 구조로 보관합니다.</p>
 */
public final class TcModelRuntime {

    private final long modelKey;
    private final String modelName;
    private final String modelVersion;
    private final ProtocolType protocolType;

    private final Map<String, List<WorkflowRuntimeEntry>> workflowsByMessageName;
    private final Map<SecsWorkflowKey, List<WorkflowRuntimeEntry>> secsWorkflowsByKey;
    private final Map<String, TcModelSecsMessage> secsMessagesByName;
    private final Map<String, TcModelSocketMessage> socketMessagesByName;
    private final Map<VariableRuntimeKey, TcModelVariableId> variableIds;

    private TcModelRuntime(
            final long modelKey,
            final String modelName,
            final String modelVersion,
            final ProtocolType protocolType,
            final Map<String, List<WorkflowRuntimeEntry>> workflowsByMessageName,
            final Map<SecsWorkflowKey, List<WorkflowRuntimeEntry>> secsWorkflowsByKey,
            final Map<String, TcModelSecsMessage> secsMessagesByName,
            final Map<String, TcModelSocketMessage> socketMessagesByName,
            final Map<VariableRuntimeKey, TcModelVariableId> variableIds
    ) {
        this.modelKey = modelKey;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.protocolType = protocolType;
        this.workflowsByMessageName = immutableListMap(workflowsByMessageName);
        this.secsWorkflowsByKey = Map.copyOf(new LinkedHashMap<>(secsWorkflowsByKey));
        this.secsMessagesByName = Map.copyOf(new LinkedHashMap<>(secsMessagesByName));
        this.socketMessagesByName = Map.copyOf(new LinkedHashMap<>(socketMessagesByName));
        this.variableIds = Map.copyOf(new LinkedHashMap<>(variableIds));
    }

    /**
     * DB 레코드 집합에서 런타임 컨텍스트를 생성합니다.
     *
     * @param model tc_model 단건
     * @param workflowEntries workflow 엔트리 목록
     * @param secsMessages tc_model_secs_message 목록
     * @param socketMessages tc_model_socket_message 목록
     * @param variableIds tc_model_variableid 목록
     * @return 불변 런타임 컨텍스트
     */
    public static TcModelRuntime from(
            final TcModel model,
            final List<WorkflowRuntimeEntry> workflowEntries,
            final List<TcModelSecsMessage> secsMessages,
            final List<TcModelSocketMessage> socketMessages,
            final List<TcModelVariableId> variableIds
    ) {
        Objects.requireNonNull(model, "model is null");
        Objects.requireNonNull(workflowEntries, "workflowEntries is null");
        Objects.requireNonNull(secsMessages, "secsMessages is null");
        Objects.requireNonNull(socketMessages, "socketMessages is null");
        Objects.requireNonNull(variableIds, "variableIds is null");

        final List<WorkflowRuntimeEntry> sortedEntries = new ArrayList<>(workflowEntries);
        sortedEntries.sort(Comparator.comparingInt(WorkflowRuntimeEntry::order).thenComparingLong(WorkflowRuntimeEntry::workflowKey));

        final Map<String, List<WorkflowRuntimeEntry>> workflowsByMessageName = new LinkedHashMap<>();
        final Map<SecsWorkflowKey, List<WorkflowRuntimeEntry>> secsWorkflowsByKey = new LinkedHashMap<>();
        for (WorkflowRuntimeEntry entry : sortedEntries) {
            workflowsByMessageName
                    .computeIfAbsent(entry.messageName(), ignored -> new ArrayList<>())
                    .add(entry);

            if (entry.isSecsSpecific()) {
                final SecsWorkflowKey key = SecsWorkflowKey.of(
                        entry.messageName(),
                        entry.eventId(),
                        entry.transactionId()
                );
                secsWorkflowsByKey
                        .computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(entry);
            }
        }

        final Map<String, TcModelSecsMessage> secsMessagesByName = new LinkedHashMap<>();
        for (TcModelSecsMessage message : secsMessages) {
            if (message == null || message.secsMsgName() == null || message.secsMsgName().isBlank()) {
                continue;
            }
            secsMessagesByName.put(message.secsMsgName().trim(), message);
        }

        final Map<String, TcModelSocketMessage> socketMessagesByName = new LinkedHashMap<>();
        for (TcModelSocketMessage message : socketMessages) {
            if (message == null || message.socketMsgName() == null || message.socketMsgName().isBlank()) {
                continue;
            }
            socketMessagesByName.put(message.socketMsgName().trim(), message);
        }

        final Map<VariableRuntimeKey, TcModelVariableId> variableIndex = new LinkedHashMap<>();
        for (TcModelVariableId variableId : variableIds) {
            if (variableId == null || variableId.variableIdType() == null || variableId.variableId() == null) {
                continue;
            }
            variableIndex.put(VariableRuntimeKey.of(variableId.variableIdType(), variableId.variableId()), variableId);
        }

        return new TcModelRuntime(
                model.modelKey(),
                model.modelName(),
                model.modelVersion(),
                model.commInterface(),
                workflowsByMessageName,
                secsWorkflowsByKey,
                secsMessagesByName,
                socketMessagesByName,
                variableIndex
        );
    }

    /**
     * modelKey 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long modelKey() {
        return modelKey;
    }

    /**
     * modelName 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String modelName() {
        return modelName;
    }

    /**
     * modelVersion 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String modelVersion() {
        return modelVersion;
    }

    /**
     * protocolType 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public ProtocolType protocolType() {
        return protocolType;
    }

    /**
     * workflowsByMessageName 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Map<String, List<WorkflowRuntimeEntry>> workflowsByMessageName() {
        return workflowsByMessageName;
    }

    /**
     * secsWorkflowsByKey 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Map<SecsWorkflowKey, List<WorkflowRuntimeEntry>> secsWorkflowsByKey() {
        return secsWorkflowsByKey;
    }

    /**
     * secsMessagesByName 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Map<String, TcModelSecsMessage> secsMessagesByName() {
        return secsMessagesByName;
    }

    /**
     * socketMessagesByName 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Map<String, TcModelSocketMessage> socketMessagesByName() {
        return socketMessagesByName;
    }

    /**
     * variableIds 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Map<VariableRuntimeKey, TcModelVariableId> variableIds() {
        return variableIds;
    }

    /**
     * messageName 기준 workflow 후보를 조회합니다.
     *
     * @param messageName message name
     * @return 후보 목록(없으면 빈 목록)
     */
    public List<WorkflowRuntimeEntry> findWorkflowsByMessageName(final String messageName) {
        if (messageName == null || messageName.isBlank()) {
            return List.of();
        }
        return workflowsByMessageName.getOrDefault(messageName.trim(), List.of());
    }

    /**
     * SECS 상세키 기준 workflow 후보를 조회합니다.
     *
     * @param messageName message name
     * @param eventId event id
     * @param transactionId transaction id
     * @return 후보 목록(없으면 빈 목록)
     */
    public List<WorkflowRuntimeEntry> findSecsWorkflows(
            final String messageName,
            final String eventId,
            final String transactionId
    ) {
        if (messageName == null || messageName.isBlank()) {
            return List.of();
        }
        final SecsWorkflowKey key = SecsWorkflowKey.of(messageName, eventId, transactionId);
        return secsWorkflowsByKey.getOrDefault(key, List.of());
    }

    /**
     * messageName 기준 workflow 존재 여부를 반환합니다.
     *
     * @param messageName message name
     * @return 존재 여부
     */
    public boolean hasWorkflow(final String messageName) {
        return !findWorkflowsByMessageName(messageName).isEmpty();
    }

    /**
     * variable id 인덱스를 조회합니다.
     *
     * @param variableIdType variable id type
     * @param variableId variable id text
     * @return variable(optional)
     */
    public Optional<TcModelVariableId> findVariable(
            final VariableIdType variableIdType,
            final String variableId
    ) {
        if (variableIdType == null || variableId == null || variableId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(variableIds.get(VariableRuntimeKey.of(variableIdType, variableId)));
    }

    private static Map<String, List<WorkflowRuntimeEntry>> immutableListMap(
            final Map<String, List<WorkflowRuntimeEntry>> source
    ) {
        final Map<String, List<WorkflowRuntimeEntry>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<WorkflowRuntimeEntry>> entry : source.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copied);
    }

    /**
     * variable id 복합키입니다.
     */
    public record VariableRuntimeKey(
            VariableIdType variableIdType,
            String variableId
    ) {
        public VariableRuntimeKey {
            Objects.requireNonNull(variableIdType, "variableIdType is null");
            if (variableId == null || variableId.isBlank()) {
                throw new IllegalArgumentException("variableId is required");
            }
            variableId = variableId.trim();
        }

        public static VariableRuntimeKey of(
                final VariableIdType variableIdType,
                final String variableId
        ) {
            return new VariableRuntimeKey(variableIdType, variableId);
        }
    }
}

