package com.nori.tc.db.mybatis.common.store.model;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TcModelMybatisStore}의 enum 저장 입력 구성을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class TcModelMybatisStoreTest {

    @Mock
    private TcModelMapper mapper;

    @Test
    @DisplayName("tc_model upsert는 SECS/DEPRECATED enum 값을 그대로 저장 입력에 반영합니다")
    void upsertUsesUpdatedProtocolAndStatusEnums() {
        final TcModelMybatisStore store = new TcModelMybatisStore(mapper);
        final UpsertTcModel command = new UpsertTcModel(
                null,
                "MODEL-SAVE-001",
                "ROOT-MODEL",
                "v1",
                ProtocolType.SECS,
                ModelStatus.DEPRECATED,
                "test-description",
                "NORI",
                "TEST",
                "TEST"
        );

        final TcModel savedRow = new TcModel(
                101L,
                10L,
                "MODEL-SAVE-001",
                "ROOT-MODEL",
                "v1",
                ProtocolType.SECS,
                ModelStatus.DEPRECATED,
                "test-description",
                "NORI",
                null,
                null,
                "TEST",
                "TEST"
        );
        when(mapper.findByNameVersion("MODEL-SAVE-001", "v1")).thenReturn(Optional.of(savedRow));

        store.upsert(command);

        final ArgumentCaptor<TcModel> rowCaptor = ArgumentCaptor.forClass(TcModel.class);
        verify(mapper).insert(rowCaptor.capture());

        final TcModel insertRow = rowCaptor.getValue();
        assertEquals("MODEL-SAVE-001", insertRow.modelName());
        assertEquals("ROOT-MODEL", insertRow.parentModel());
        assertEquals(ProtocolType.SECS, insertRow.commInterface());
        assertEquals(ModelStatus.DEPRECATED, insertRow.status());
    }
}
