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
 * 모델 버전 단위 런타임 컨텍스트입니다.
 *
 * <p>
 * DB를 반복 조회하지 않도록 워크플로우/메시지/변수/MDF를 메모리 인덱스로 보관합니다.
 * </p>
 */
public final class TcModelRuntime {

    private final long modelVersionKey;
    private final String modelName;
    private final String modelVersion;
    private final ProtocolType protocolType;

    private final Map<String, List<WorkflowRuntimeEntry>> workflowsByMessageName;
    private final Map<SecsWorkflowKey, List<WorkflowRuntimeEntry>> secsWorkflowsByKey;
    private final Map<String, TcModelSecsMessage> secsMessagesByName;
    private final Map<String, TcModelSocketMessage> socketMessagesByName;
    private final Map<VariableRuntimeKey, TcModelVariableId> variableIds;
    private final MdfRuntimeDefinition mdfRuntimeDefinition;

    private TcModelRuntime(
            long modelVersionKey,
            String modelName,
            String modelVersion,
            ProtocolType protocolType,
            Map<String, List<WorkflowRuntimeEntry>> workflowsByMessageName,
            Map<SecsWorkflowKey, List<WorkflowRuntimeEntry>> secsWorkflowsByKey,
            Map<String, TcModelSecsMessage> secsMessagesByName,
            Map<String, TcModelSocketMessage> socketMessagesByName,
            Map<VariableRuntimeKey, TcModelVariableId> variableIds,
            MdfRuntimeDefinition mdfRuntimeDefinition
    ) {
        this.modelVersionKey = modelVersionKey;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.protocolType = protocolType;
        this.workflowsByMessageName = immutableListMap(workflowsByMessageName);
        this.secsWorkflowsByKey = Map.copyOf(new LinkedHashMap<>(secsWorkflowsByKey));
        this.secsMessagesByName = Map.copyOf(new LinkedHashMap<>(secsMessagesByName));
        this.socketMessagesByName = Map.copyOf(new LinkedHashMap<>(socketMessagesByName));
        this.variableIds = Map.copyOf(new LinkedHashMap<>(variableIds));
        this.mdfRuntimeDefinition = mdfRuntimeDefinition == null ? MdfRuntimeDefinition.empty() : mdfRuntimeDefinition;
    }

    /**
     * MDF 정의 없이 런타임 컨텍스트를 생성합니다.
     */
    public static TcModelRuntime from(
            TcModel model,
            List<WorkflowRuntimeEntry> workflowEntries,
            List<TcModelSecsMessage> secsMessages,
            List<TcModelSocketMessage> socketMessages,
            List<TcModelVariableId> variableIds
    ) {
        return from(
                model,
                workflowEntries,
                secsMessages,
                socketMessages,
                variableIds,
                MdfRuntimeDefinition.empty()
        );
    }

    /**
     * MDF 정의를 포함해 런타임 컨텍스트를 생성합니다.
     */
    public static TcModelRuntime from(
            TcModel model,
            List<WorkflowRuntimeEntry> workflowEntries,
            List<TcModelSecsMessage> secsMessages,
            List<TcModelSocketMessage> socketMessages,
            List<TcModelVariableId> variableIds,
            MdfRuntimeDefinition mdfRuntimeDefinition
    ) {
        Objects.requireNonNull(model, "model is null");
        Objects.requireNonNull(workflowEntries, "workflowEntries is null");
        Objects.requireNonNull(secsMessages, "secsMessages is null");
        Objects.requireNonNull(socketMessages, "socketMessages is null");
        Objects.requireNonNull(variableIds, "variableIds is null");

        final List<WorkflowRuntimeEntry> sortedEntries = new ArrayList<>(workflowEntries);
        sortedEntries.sort(Comparator.comparingInt(WorkflowRuntimeEntry::order)
                .thenComparingLong(WorkflowRuntimeEntry::workflowKey));

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
                model.modelVersionKey(),
                model.modelName(),
                model.modelVersion(),
                model.commInterface(),
                workflowsByMessageName,
                secsWorkflowsByKey,
                secsMessagesByName,
                socketMessagesByName,
                variableIndex,
                mdfRuntimeDefinition
        );
    }

    /**
     * 모델 버전 키를 반환합니다.
     */
    public long modelVersionKey() {
        return modelVersionKey;
    }

    /**
     * 모델 이름을 반환합니다.
     */
    public String modelName() {
        return modelName;
    }

    /**
     * 모델 버전을 반환합니다.
     */
    public String modelVersion() {
        return modelVersion;
    }

    /**
     * 프로토콜 타입을 반환합니다.
     */
    public ProtocolType protocolType() {
        return protocolType;
    }

    /**
     * messageName 기준 워크플로우 인덱스를 반환합니다.
     */
    public Map<String, List<WorkflowRuntimeEntry>> workflowsByMessageName() {
        return workflowsByMessageName;
    }

    /**
     * SECS 키(eventId/transactionId) 기준 워크플로우 인덱스를 반환합니다.
     */
    public Map<SecsWorkflowKey, List<WorkflowRuntimeEntry>> secsWorkflowsByKey() {
        return secsWorkflowsByKey;
    }

    /**
     * SECS 메시지 인덱스를 반환합니다.
     */
    public Map<String, TcModelSecsMessage> secsMessagesByName() {
        return secsMessagesByName;
    }

    /**
     * SOCKET 메시지 인덱스를 반환합니다.
     */
    public Map<String, TcModelSocketMessage> socketMessagesByName() {
        return socketMessagesByName;
    }

    /**
     * 변수 인덱스를 반환합니다.
     */
    public Map<VariableRuntimeKey, TcModelVariableId> variableIds() {
        return variableIds;
    }

    /**
     * MDF 메시지 정의 집합을 반환합니다.
     */
    public MdfRuntimeDefinition mdfRuntimeDefinition() {
        return mdfRuntimeDefinition;
    }

    /**
     * MDF 메시지 이름으로 정의를 조회합니다.
     */
    public Optional<MdfRuntimeDefinition.MdfMessageDefinition> findMdfMessage(String messageName) {
        return mdfRuntimeDefinition.findMessage(messageName);
    }

    /**
     * messageName 기준 workflow 목록을 조회합니다.
     */
    public List<WorkflowRuntimeEntry> findWorkflowsByMessageName(String messageName) {
        if (messageName == null || messageName.isBlank()) {
            return List.of();
        }
        return workflowsByMessageName.getOrDefault(messageName.trim(), List.of());
    }

    /**
     * SECS 키 기준 workflow 목록을 조회합니다.
     */
    public List<WorkflowRuntimeEntry> findSecsWorkflows(
            String messageName,
            String eventId,
            String transactionId
    ) {
        if (messageName == null || messageName.isBlank()) {
            return List.of();
        }
        final SecsWorkflowKey key = SecsWorkflowKey.of(messageName, eventId, transactionId);
        return secsWorkflowsByKey.getOrDefault(key, List.of());
    }

    /**
     * messageName 기준 workflow 존재 여부를 반환합니다.
     */
    public boolean hasWorkflow(String messageName) {
        return !findWorkflowsByMessageName(messageName).isEmpty();
    }

    /**
     * 변수 타입/이름으로 변수 정의를 조회합니다.
     */
    public Optional<TcModelVariableId> findVariable(
            VariableIdType variableIdType,
            String variableId
    ) {
        if (variableIdType == null || variableId == null || variableId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(variableIds.get(VariableRuntimeKey.of(variableIdType, variableId)));
    }

    /**
     * 리스트 값을 포함한 Map을 깊은 불변 구조로 변환합니다.
     */
    private static Map<String, List<WorkflowRuntimeEntry>> immutableListMap(
            Map<String, List<WorkflowRuntimeEntry>> source
    ) {
        final Map<String, List<WorkflowRuntimeEntry>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<WorkflowRuntimeEntry>> entry : source.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copied);
    }

    /**
     * 변수 인덱스 키입니다.
     */
    public record VariableRuntimeKey(
            VariableIdType variableIdType,
            String variableId
    ) {

        /**
         * 생성 시 필수 값을 검증합니다.
         */
        public VariableRuntimeKey {
            Objects.requireNonNull(variableIdType, "variableIdType is null");
            if (variableId == null || variableId.isBlank()) {
                throw new IllegalArgumentException("variableId is required");
            }
            variableId = variableId.trim();
        }

        /**
         * 정적 팩토리 메서드입니다.
         */
        public static VariableRuntimeKey of(
                VariableIdType variableIdType,
                String variableId
        ) {
            return new VariableRuntimeKey(variableIdType, variableId);
        }
    }
}
