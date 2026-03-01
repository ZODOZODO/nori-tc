package com.nori.tc.db.mybatis.common.store.model;

import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelMdfMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TcModelMdfMybatisStore} 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class TcModelMdfMybatisStoreTest {

    @Mock
    private TcModelMdfMapper mapper;

    @Test
    void shouldUpsertByModelVersionKeyWhenMdfKeyIsAbsent() {
        final TcModelMdfMybatisStore store = new TcModelMdfMybatisStore(mapper);

        final TcModelMdf existing = new TcModelMdf(
                11L,
                100L,
                "MDF_MAIN",
                "<mdf/>".getBytes(),
                OffsetDateTime.now()
        );

        when(mapper.findByModelVersionKey(100L)).thenReturn(Optional.of(existing));
        when(mapper.update(any())).thenReturn(1);

        final TcModelMdf result = store.upsert(new UpsertTcModelMdf(
                null,
                100L,
                "MDF_MAIN",
                "<mdf><A_EQP>CMD={EQPID}</A_EQP></mdf>".getBytes()
        ));

        Assertions.assertEquals(100L, result.modelVersionKey());
        verify(mapper, times(2)).findByModelVersionKey(100L);
        verify(mapper).update(any());
    }

    @Test
    void shouldFindByModelVersionKey() {
        final TcModelMdfMybatisStore store = new TcModelMdfMybatisStore(mapper);
        final TcModelMdf row = new TcModelMdf(1L, 200L, "MDF_MAIN", "<mdf/>".getBytes(), OffsetDateTime.now());

        when(mapper.findByModelVersionKey(200L)).thenReturn(Optional.of(row));

        final Optional<TcModelMdf> found = store.findByModelVersionKey(200L);
        Assertions.assertTrue(found.isPresent());
        Assertions.assertEquals(200L, found.orElseThrow().modelVersionKey());
    }
}
