package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.store.TcModelSecsMessageStore;
import com.nori.tc.db.core.model.store.TcModelSocketMessageStore;
import com.nori.tc.db.core.model.store.TcModelStore;
import com.nori.tc.db.core.model.store.TcModelVariableIdStore;
import com.nori.tc.db.core.model.store.TcModelWorkflowStore;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelVariableId;
import com.nori.tc.db.domain.model.TcModelWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * DB 레코드를 {@link TcModelRuntime}로 조립하는 어셈블러입니다.
 */
@Component
public class BusinessModelRuntimeAssembler {

    private static final Logger log = LoggerFactory.getLogger(BusinessModelRuntimeAssembler.class);

    private final TcModelStore modelStore;
    private final TcModelWorkflowStore workflowStore;
    private final TcModelSecsMessageStore secsMessageStore;
    private final TcModelSocketMessageStore socketMessageStore;
    private final TcModelVariableIdStore variableIdStore;
    private final BusinessModelCacheProperties cacheProperties;

    /**
     * DB store 포트를 주입받습니다.
     */
    public BusinessModelRuntimeAssembler(
            final TcModelStore modelStore,
            final TcModelWorkflowStore workflowStore,
            final TcModelSecsMessageStore secsMessageStore,
            final TcModelSocketMessageStore socketMessageStore,
            final TcModelVariableIdStore variableIdStore,
            final BusinessModelCacheProperties cacheProperties
    ) {
        this.modelStore = Objects.requireNonNull(modelStore, "modelStore is null");
        this.workflowStore = Objects.requireNonNull(workflowStore, "workflowStore is null");
        this.secsMessageStore = Objects.requireNonNull(secsMessageStore, "secsMessageStore is null");
        this.socketMessageStore = Objects.requireNonNull(socketMessageStore, "socketMessageStore is null");
        this.variableIdStore = Objects.requireNonNull(variableIdStore, "variableIdStore is null");
        this.cacheProperties = Objects.requireNonNull(cacheProperties, "cacheProperties is null");
    }

    /**
     * modelKey 기준으로 런타임 컨텍스트를 조립합니다.
     *
     * @param modelKey model key
     * @return 조립된 runtime
     */
    public TcModelRuntime assemble(final long modelKey) {
        if (modelKey <= 0L) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }

        final TcModel model = modelStore.findByModelKey(modelKey)
                .orElseThrow(() -> new IllegalStateException("tc_model not found. modelKey=" + modelKey));

        final List<TcModelWorkflow> workflows = loadAllByPage(page -> workflowStore.findAllByModelKey(modelKey, page));
        workflows.sort(Comparator.comparingLong(TcModelWorkflow::workflowKey));

        final List<WorkflowRuntimeEntry> entries = new ArrayList<>(workflows.size());
        for (int i = 0; i < workflows.size(); i++) {
            entries.add(WorkflowRuntimeEntry.from(workflows.get(i), i));
        }

        final List<TcModelSecsMessage> secsMessages = loadAllByPage(page -> secsMessageStore.findAllByModelKey(modelKey, page));
        final List<TcModelSocketMessage> socketMessages = loadAllByPage(page -> socketMessageStore.findAllByModelKey(modelKey, page));
        final List<TcModelVariableId> variableIds = loadAllByPage(page -> variableIdStore.findAllByModelKey(modelKey, page));

        if (log.isDebugEnabled()) {
            log.debug("Assembled model runtime. modelKey={}, workflows={}, secsMessages={}, socketMessages={}, variables={}",
                    modelKey,
                    entries.size(),
                    secsMessages.size(),
                    socketMessages.size(),
                    variableIds.size());
        }

        return TcModelRuntime.from(
                model,
                entries,
                secsMessages,
                socketMessages,
                variableIds
        );
    }

    private <T> List<T> loadAllByPage(final Function<PageRequest, List<T>> pageLoader) {
        final int limit = cacheProperties.getPageSize();
        final List<T> results = new ArrayList<>();
        int offset = 0;

        while (true) {
            final List<T> page = pageLoader.apply(PageRequest.of(offset, limit));
            if (page == null || page.isEmpty()) {
                break;
            }

            results.addAll(page);

            if (page.size() < limit) {
                break;
            }
            offset += limit;
        }
        return results;
    }
}

