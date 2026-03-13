package com.nori.tc.db.jpa.common.store.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.nori.tc.db.core.model.upsert.UpsertTcModelDcopItem;
import com.nori.tc.db.domain.common.model.DcopCalculationRule;
import com.nori.tc.db.domain.common.model.DcopCollectionRule;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import com.nori.tc.db.jpa.common.entity.model.TcModelDcopItemEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelDcopItemEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelDcopItemJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TcModelDcopItemJpaStore} 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class TcModelDcopItemJpaStoreTest {

    @Mock
    private TcModelDcopItemJpaRepository repository;

    @Mock
    private TcModelDcopItemEntityMapper mapper;

    @Test
    void shouldInitializeUniqueKeyFieldsWhenCreatingNewEntity() {
        final TcModelDcopItemJpaStore store = new TcModelDcopItemJpaStore(repository, mapper);
        final UpsertTcModelDcopItem command = new UpsertTcModelDcopItem(
                100L,
                "DCOP_MAIN",
                "WF_MAIN",
                "EVT_100",
                "VID_TEMP",
                DcopCollectionRule.LAST,
                DcopCalculationRule.NONE,
                1
        );
        final TcModelDcopItemEntity savedEntity = TcModelDcopItemEntity.newEntity(100L, "DCOP_MAIN");
        final TcModelDcopItem savedDomain = new TcModelDcopItem(
                1L,
                100L,
                "DCOP_MAIN",
                "WF_MAIN",
                "EVT_100",
                "VID_TEMP",
                DcopCollectionRule.LAST,
                DcopCalculationRule.NONE,
                1,
                OffsetDateTime.now()
        );

        when(repository.findByModelVersionKeyAndDcopItemName(100L, "DCOP_MAIN"))
                .thenReturn(Optional.empty());
        when(repository.save(any(TcModelDcopItemEntity.class))).thenReturn(savedEntity);
        when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

        final TcModelDcopItem result = store.upsert(command);

        final ArgumentCaptor<TcModelDcopItemEntity> entityCaptor =
                ArgumentCaptor.forClass(TcModelDcopItemEntity.class);
        verify(mapper).updateEntity(any(), entityCaptor.capture());
        assertEquals(100L, entityCaptor.getValue().getModelVersionKey());
        assertEquals("DCOP_MAIN", entityCaptor.getValue().getDcopItemName());
        assertEquals(100L, result.modelVersionKey());
    }
}
