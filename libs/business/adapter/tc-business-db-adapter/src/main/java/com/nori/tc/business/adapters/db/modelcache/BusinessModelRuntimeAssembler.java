package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.store.TcModelMdfStore;
import com.nori.tc.db.core.model.store.TcModelSecsMessageStore;
import com.nori.tc.db.core.model.store.TcModelSocketMessageStore;
import com.nori.tc.db.core.model.store.TcModelStore;
import com.nori.tc.db.core.model.store.TcModelVariableIdStore;
import com.nori.tc.db.core.model.store.TcModelWorkflowStore;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelMdf;
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
 *
 * <p>
 * 조립 범위:
 * 1) tc_model
 * 2) workflow/secs/socket/variable 인덱스
 * 3) MDF(XML) 파싱 결과
 * </p>
 */
@Component
public class BusinessModelRuntimeAssembler {

    private static final Logger log = LoggerFactory.getLogger(BusinessModelRuntimeAssembler.class);

    private final TcModelStore modelStore;
    private final TcModelWorkflowStore workflowStore;
    private final TcModelSecsMessageStore secsMessageStore;
    private final TcModelSocketMessageStore socketMessageStore;
    private final TcModelVariableIdStore variableIdStore;
    private final TcModelMdfStore mdfStore;
    private final BusinessMdfRuntimeParser mdfRuntimeParser;
    private final BusinessModelCacheProperties cacheProperties;

    /**
     * 어셈블러 의존성을 주입받습니다.
     */
    public BusinessModelRuntimeAssembler(
            TcModelStore modelStore,
            TcModelWorkflowStore workflowStore,
            TcModelSecsMessageStore secsMessageStore,
            TcModelSocketMessageStore socketMessageStore,
            TcModelVariableIdStore variableIdStore,
            TcModelMdfStore mdfStore,
            BusinessMdfRuntimeParser mdfRuntimeParser,
            BusinessModelCacheProperties cacheProperties
    ) {
        this.modelStore = Objects.requireNonNull(modelStore, "modelStore is null");
        this.workflowStore = Objects.requireNonNull(workflowStore, "workflowStore is null");
        this.secsMessageStore = Objects.requireNonNull(secsMessageStore, "secsMessageStore is null");
        this.socketMessageStore = Objects.requireNonNull(socketMessageStore, "socketMessageStore is null");
        this.variableIdStore = Objects.requireNonNull(variableIdStore, "variableIdStore is null");
        this.mdfStore = Objects.requireNonNull(mdfStore, "mdfStore is null");
        this.mdfRuntimeParser = Objects.requireNonNull(mdfRuntimeParser, "mdfRuntimeParser is null");
        this.cacheProperties = Objects.requireNonNull(cacheProperties, "cacheProperties is null");
    }

    /**
     * modelVersionKey 기준 런타임 컨텍스트를 조립합니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 조립된 런타임 컨텍스트
     */
    public TcModelRuntime assemble(final long modelVersionKey) {
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }

        final TcModel model = modelStore.findByModelVersionKey(modelVersionKey)
                .orElseThrow(() -> new IllegalStateException(
                        "tc_model not found. modelVersionKey=" + modelVersionKey
                ));

        final TcModelMdf mdf = mdfStore.findByModelVersionKey(modelVersionKey)
                .orElseThrow(() -> new IllegalStateException(
                        "tc_model_mdf not found. modelVersionKey=" + modelVersionKey
                ));

        final MdfRuntimeDefinition mdfRuntimeDefinition = mdfRuntimeParser.parse(
                modelVersionKey,
                mdf.mdfName(),
                mdf.mdfFile()
        );

        final List<TcModelWorkflow> workflows = loadAllByPage(page -> workflowStore.findAllByModelVersionKey(modelVersionKey, page));
        workflows.sort(Comparator.comparingLong(TcModelWorkflow::workflowKey));

        final List<WorkflowRuntimeEntry> entries = new ArrayList<>(workflows.size());
        for (int i = 0; i < workflows.size(); i++) {
            entries.add(WorkflowRuntimeEntry.from(workflows.get(i), i));
        }

        final List<TcModelSecsMessage> secsMessages = loadAllByPage(page -> secsMessageStore.findAllByModelVersionKey(modelVersionKey, page));
        final List<TcModelSocketMessage> socketMessages = loadAllByPage(page -> socketMessageStore.findAllByModelVersionKey(modelVersionKey, page));
        final List<TcModelVariableId> variableIds = loadAllByPage(page -> variableIdStore.findAllByModelVersionKey(modelVersionKey, page));

        if (log.isDebugEnabled()) {
            log.debug("Assembled model runtime. modelVersionKey={}, workflows={}, secsMessages={}, socketMessages={}, variables={}, mdfName={}, mdfMessages={}",
                    modelVersionKey,
                    entries.size(),
                    secsMessages.size(),
                    socketMessages.size(),
                    variableIds.size(),
                    mdf.mdfName(),
                    mdfRuntimeDefinition.messagesByName().size());
        }

        return TcModelRuntime.from(
                model,
                entries,
                secsMessages,
                socketMessages,
                variableIds,
                mdfRuntimeDefinition
        );
    }

    /**
     * 공통 페이지 로더를 이용해 전체 목록을 수집합니다.
     */
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
