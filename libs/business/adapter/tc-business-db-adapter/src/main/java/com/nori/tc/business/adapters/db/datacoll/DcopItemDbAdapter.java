package com.nori.tc.business.adapters.db.datacoll;

import com.nori.tc.business.core.datacoll.DcopItemPort;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.store.TcModelDcopItemStore;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * DCOP Item 조회 포트의 DB 어댑터 구현체입니다.
 *
 * <p>{@link TcModelDcopItemStore}를 통해 DB에서 DCOP Item을 조회합니다.</p>
 *
 * <p>모든 항목을 단일 페이지로 조회합니다. 모델당 DCOP Item 수는 운영 환경 기준
 * 수백 수준이므로 최대 {@value #MAX_PAGE_SIZE}개로 충분합니다.</p>
 *
 * <p>조회 결과 정렬 기준: {@code orderRule ASC, dcopItemName ASC} (DB 레벨에서 처리)</p>
 */
@Component
public class DcopItemDbAdapter implements DcopItemPort {

    private static final Logger log = LoggerFactory.getLogger(DcopItemDbAdapter.class);

    /**
     * 모델당 DCOP Item 최대 조회 수.
     * 운영 환경에서 모델당 DCOP Item이 이 수를 초과할 경우 재검토가 필요합니다.
     */
    private static final int MAX_PAGE_SIZE = 1000;

    private final TcModelDcopItemStore dcopItemStore;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param dcopItemStore DCOP Item DB 저장소
     */
    public DcopItemDbAdapter(final TcModelDcopItemStore dcopItemStore) {
        this.dcopItemStore = Objects.requireNonNull(dcopItemStore, "dcopItemStore is null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>정렬 기준: {@code orderRule ASC, dcopItemName ASC} (DB 레벨 정렬)</p>
     */
    @Override
    public List<TcModelDcopItem> findAllByModelVersionKey(final long modelVersionKey) {
        log.debug("DCOP Item 목록 조회. modelVersionKey={}", modelVersionKey);

        final List<TcModelDcopItem> items = dcopItemStore.findAllByModelVersionKey(
                modelVersionKey, PageRequest.of(0, MAX_PAGE_SIZE));

        log.debug("DCOP Item 목록 조회 완료. modelVersionKey={}, count={}", modelVersionKey, items.size());
        return items;
    }
}
