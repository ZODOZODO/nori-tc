package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqp;
import com.nori.tc.db.core.model.store.TcModelSecsMessageStore;
import com.nori.tc.db.core.model.store.TcModelSocketMessageStore;
import com.nori.tc.db.core.model.store.TcModelStore;
import com.nori.tc.db.core.model.store.TcModelVariableIdStore;
import com.nori.tc.db.core.model.store.TcModelWorkflowStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSecsMessage;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSocketMessage;
import com.nori.tc.db.core.model.upsert.UpsertTcModelVariableId;
import com.nori.tc.db.core.model.upsert.UpsertTcModelWorkflow;
import com.nori.tc.db.domain.common.model.VariableIdType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelVariableId;
import com.nori.tc.db.domain.model.TcModelWorkflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * model runtime cache 단위 테스트용 in-memory store fixture 모음입니다.
 */
final class ModelCacheTestFixtures {

    /**
     * ModelCacheTestFixtures 생성자를 초기화합니다.
     *
     */

    private ModelCacheTestFixtures() {
        throw new IllegalStateException("ModelCacheTestFixtures cannot be instantiated");
    }

    static List<TcEqp> pageEqp(final List<TcEqp> source, final PageRequest pageRequest) {
        return page(source, pageRequest);
    }

    static List<TcModelWorkflow> pageWorkflow(final List<TcModelWorkflow> source, final PageRequest pageRequest) {
        return page(source, pageRequest);
    }

    static List<TcModelSecsMessage> pageSecs(final List<TcModelSecsMessage> source, final PageRequest pageRequest) {
        return page(source, pageRequest);
    }

    static List<TcModelSocketMessage> pageSocket(
            final List<TcModelSocketMessage> source,
            final PageRequest pageRequest
    ) {
        return page(source, pageRequest);
    }

    static List<TcModelVariableId> pageVariable(
            final List<TcModelVariableId> source,
            final PageRequest pageRequest
    ) {
        return page(source, pageRequest);
    }

    /**
     * page 기능을 수행합니다.
     *
     * @param source 입력 값
     * @param pageRequest 입력 값
     * @return 처리 결과
     */

    private static <T> List<T> page(final List<T> source, final PageRequest pageRequest) {
        final int from = Math.min(pageRequest.offset(), source.size());
        final int to = Math.min(from + pageRequest.limit(), source.size());
        return List.copyOf(source.subList(from, to));
    }

    /**
     * {@link TcEqpStore} in-memory 구현입니다.
     */
    static final class InMemoryEqpStore implements TcEqpStore {
        private final List<TcEqp> eqps;

        InMemoryEqpStore(final List<TcEqp> eqps) {
            this.eqps = new ArrayList<>(eqps);
            this.eqps.sort(Comparator.comparing(e -> e.eqpId() == null ? "" : e.eqpId()));
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcEqp upsert(final UpsertTcEqp command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByEqpId 기능을 수행합니다.
         *
         * @param eqpId 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcEqp> findByEqpId(final String eqpId) {
            return eqps.stream()
                    .filter(eqp -> eqpId != null && eqpId.equals(eqp.eqpId()))
                    .findFirst();
        }

        /**
         * findAll 기능을 수행합니다.
         *
         * @param page 입력 값
         * @return 처리 결과
         */

        @Override
        public List<TcEqp> findAll(final PageRequest page) {
            return pageEqp(eqps, page);
        }

        /**
         * route_partition + enabled 조건으로 설비 목록을 조회합니다.
         *
         * <p>Model cache 테스트에서는 Gateway 전용 라우팅을 직접 사용하지 않지만,
         * 공통 저장소 인터페이스 변경(U3)에 맞춰 테스트 더블도 계약을 만족해야 합니다.</p>
         * <p>구현은 단순 필터 + 기존 페이지 유틸 재사용으로 최소 동작만 제공합니다.</p>
         *
         * @param routePartitions 조회 대상 route_partition 목록
         * @param enabled enabled 필터 값
         * @param page 페이징 요청
         * @return 필터링된 설비 목록(페이지 적용)
         */
        @Override
        public List<TcEqp> findAllByRoutePartitionsAndEnabled(
                final List<Integer> routePartitions,
                final boolean enabled,
                final PageRequest page
        ) {
            if (routePartitions == null || routePartitions.isEmpty()) {
                return List.of();
            }

            final List<TcEqp> filtered = eqps.stream()
                    .filter(eqp -> eqp.enabled() == enabled)
                    .filter(eqp -> eqp.routePartition() != null && routePartitions.contains(eqp.routePartition()))
                    .toList();
            return pageEqp(filtered, page);
        }

        /**
         * deleteByEqpId 기능을 수행합니다.
         *
         * @param eqpId 입력 값
         */

        @Override
        public void deleteByEqpId(final String eqpId) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * {@link TcModelStore} in-memory 구현입니다.
     */
    static final class InMemoryModelStore implements TcModelStore {
        private final Map<Long, TcModel> byModelKey;

        InMemoryModelStore(final List<TcModel> models) {
            final Map<Long, TcModel> mapped = new LinkedHashMap<>();
            for (TcModel model : models) {
                mapped.put(model.modelKey(), model);
            }
            this.byModelKey = Map.copyOf(mapped);
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcModel upsert(final UpsertTcModel command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByModelKey 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcModel> findByModelKey(final long modelKey) {
            return Optional.ofNullable(byModelKey.get(modelKey));
        }

        /**
         * findByNameVersion 기능을 수행합니다.
         *
         * @param modelName 입력 값
         * @param modelVersion 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcModel> findByNameVersion(final String modelName, final String modelVersion) {
            return byModelKey.values().stream()
                    .filter(model -> model.modelName().equals(modelName) && model.modelVersion().equals(modelVersion))
                    .findFirst();
        }

        /**
         * findAll 기능을 수행합니다.
         *
         * @param page 입력 값
         * @return 처리 결과
         */

        @Override
        public List<TcModel> findAll(final PageRequest page) {
            final List<TcModel> values = new ArrayList<>(byModelKey.values());
            values.sort(Comparator.comparingLong(TcModel::modelKey));
            return page(values, page);
        }

        /**
         * deleteByModelKey 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         */

        @Override
        public void deleteByModelKey(final long modelKey) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * {@link TcModelWorkflowStore} in-memory 구현입니다.
     */
    static final class InMemoryWorkflowStore implements TcModelWorkflowStore {
        private final Map<Long, List<TcModelWorkflow>> byModelKey;

        InMemoryWorkflowStore(final Map<Long, List<TcModelWorkflow>> byModelKey) {
            final Map<Long, List<TcModelWorkflow>> copied = new LinkedHashMap<>();
            for (Map.Entry<Long, List<TcModelWorkflow>> entry : byModelKey.entrySet()) {
                copied.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            this.byModelKey = Map.copyOf(copied);
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcModelWorkflow upsert(final UpsertTcModelWorkflow command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByWorkflowKey 기능을 수행합니다.
         *
         * @param workflowKey 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcModelWorkflow> findByWorkflowKey(final long workflowKey) {
            return byModelKey.values().stream()
                    .flatMap(List::stream)
                    .filter(workflow -> workflow.workflowKey() == workflowKey)
                    .findFirst();
        }

        @Override
        public Optional<TcModelWorkflow> findByModelKeyAndWorkflowNameAndMessageName(
                final long modelKey,
                final String workflowName,
                final String messageName
        ) {
            return byModelKey.getOrDefault(modelKey, List.of()).stream()
                    .filter(workflow -> workflow.workflowName().equals(workflowName))
                    .filter(workflow -> workflow.messageName().equals(messageName))
                    .findFirst();
        }

        /**
         * findAllByModelKey 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         * @param page 입력 값
         * @return 처리 결과
         */

        @Override
        public List<TcModelWorkflow> findAllByModelKey(final long modelKey, final PageRequest page) {
            return pageWorkflow(byModelKey.getOrDefault(modelKey, List.of()), page);
        }

        /**
         * deleteByWorkflowKey 기능을 수행합니다.
         *
         * @param workflowKey 입력 값
         */

        @Override
        public void deleteByWorkflowKey(final long workflowKey) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * {@link TcModelSecsMessageStore} in-memory 구현입니다.
     */
    static final class InMemorySecsMessageStore implements TcModelSecsMessageStore {
        private final Map<Long, List<TcModelSecsMessage>> byModelKey;

        InMemorySecsMessageStore(final Map<Long, List<TcModelSecsMessage>> byModelKey) {
            final Map<Long, List<TcModelSecsMessage>> copied = new LinkedHashMap<>();
            for (Map.Entry<Long, List<TcModelSecsMessage>> entry : byModelKey.entrySet()) {
                copied.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            this.byModelKey = Map.copyOf(copied);
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcModelSecsMessage upsert(final UpsertTcModelSecsMessage command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findBySecsMsgKey 기능을 수행합니다.
         *
         * @param secsMsgKey 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcModelSecsMessage> findBySecsMsgKey(final long secsMsgKey) {
            return byModelKey.values().stream()
                    .flatMap(List::stream)
                    .filter(message -> message.secsMsgKey() == secsMsgKey)
                    .findFirst();
        }

        /**
         * findByModelKeyAndName 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         * @param secsMsgName 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcModelSecsMessage> findByModelKeyAndName(final long modelKey, final String secsMsgName) {
            return byModelKey.getOrDefault(modelKey, List.of()).stream()
                    .filter(message -> message.secsMsgName().equals(secsMsgName))
                    .findFirst();
        }

        /**
         * findAllByModelKey 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         * @param page 입력 값
         * @return 처리 결과
         */

        @Override
        public List<TcModelSecsMessage> findAllByModelKey(final long modelKey, final PageRequest page) {
            return pageSecs(byModelKey.getOrDefault(modelKey, List.of()), page);
        }

        /**
         * deleteBySecsMsgKey 기능을 수행합니다.
         *
         * @param secsMsgKey 입력 값
         */

        @Override
        public void deleteBySecsMsgKey(final long secsMsgKey) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * {@link TcModelSocketMessageStore} in-memory 구현입니다.
     */
    static final class InMemorySocketMessageStore implements TcModelSocketMessageStore {
        private final Map<Long, List<TcModelSocketMessage>> byModelKey;

        InMemorySocketMessageStore(final Map<Long, List<TcModelSocketMessage>> byModelKey) {
            final Map<Long, List<TcModelSocketMessage>> copied = new LinkedHashMap<>();
            for (Map.Entry<Long, List<TcModelSocketMessage>> entry : byModelKey.entrySet()) {
                copied.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            this.byModelKey = Map.copyOf(copied);
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcModelSocketMessage upsert(final UpsertTcModelSocketMessage command) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<TcModelSocketMessage> findByModelKeySocketMsgName(
                final long modelKey,
                final String socketMsgName
        ) {
            return byModelKey.getOrDefault(modelKey, List.of()).stream()
                    .filter(message -> message.socketMsgName().equals(socketMsgName))
                    .findFirst();
        }

        /**
         * findAllByModelKey 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         * @param page 입력 값
         * @return 처리 결과
         */

        @Override
        public List<TcModelSocketMessage> findAllByModelKey(final long modelKey, final PageRequest page) {
            return pageSocket(byModelKey.getOrDefault(modelKey, List.of()), page);
        }

        /**
         * deleteByModelKeySocketMsgName 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         * @param socketMsgName 입력 값
         */

        @Override
        public void deleteByModelKeySocketMsgName(final long modelKey, final String socketMsgName) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * {@link TcModelVariableIdStore} in-memory 구현입니다.
     */
    static final class InMemoryVariableIdStore implements TcModelVariableIdStore {
        private final Map<Long, List<TcModelVariableId>> byModelKey;

        InMemoryVariableIdStore(final Map<Long, List<TcModelVariableId>> byModelKey) {
            final Map<Long, List<TcModelVariableId>> copied = new LinkedHashMap<>();
            for (Map.Entry<Long, List<TcModelVariableId>> entry : byModelKey.entrySet()) {
                copied.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            this.byModelKey = Map.copyOf(copied);
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcModelVariableId upsert(final UpsertTcModelVariableId command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByVariableKey 기능을 수행합니다.
         *
         * @param variableKey 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcModelVariableId> findByVariableKey(final long variableKey) {
            return byModelKey.values().stream()
                    .flatMap(List::stream)
                    .filter(variable -> variable.variableKey() == variableKey)
                    .findFirst();
        }

        @Override
        public Optional<TcModelVariableId> findByModelKeyAndTypeAndVariableId(
                final long modelKey,
                final VariableIdType variableIdType,
                final String variableId
        ) {
            return byModelKey.getOrDefault(modelKey, List.of()).stream()
                    .filter(variable -> variable.variableIdType() == variableIdType)
                    .filter(variable -> variable.variableId().equals(variableId))
                    .findFirst();
        }

        /**
         * findAllByModelKey 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         * @param page 입력 값
         * @return 처리 결과
         */

        @Override
        public List<TcModelVariableId> findAllByModelKey(final long modelKey, final PageRequest page) {
            return pageVariable(byModelKey.getOrDefault(modelKey, List.of()), page);
        }

        /**
         * deleteByVariableKey 기능을 수행합니다.
         *
         * @param variableKey 입력 값
         */

        @Override
        public void deleteByVariableKey(final long variableKey) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
