package com.nori.tc.db.jpa.common.store.model;

import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelMdfEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelMdfJpaRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TcModelMdfJpaStore} 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class TcModelMdfJpaStoreTest {

    @Mock
    private TcModelMdfJpaRepository repository;

    @Mock
    private TcModelMdfEntityMapper mapper;

    @Test
    void shouldUpsertByModelVersionKeyWhenMdfKeyIsAbsent() {
        final TcModelMdfJpaStore store = new TcModelMdfJpaStore(repository, mapper);

        final TcModelMdfEntity entity = new TcModelMdfEntity(11L, 100L, "MDF_MAIN", "<mdf/>".getBytes());
        final TcModelMdf domain = new TcModelMdf(11L, 100L, "MDF_MAIN", "<mdf/>".getBytes(), OffsetDateTime.now());

        when(repository.findByModelVersionKey(100L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(domain);

        final TcModelMdf result = store.upsert(new UpsertTcModelMdf(
                null,
                100L,
                "MDF_MAIN",
                "<mdf><A_EQP>CMD={EQPID}</A_EQP></mdf>".getBytes()
        ));

        Assertions.assertEquals(100L, result.modelVersionKey());
        verify(repository).findByModelVersionKey(100L);
        verify(mapper).updateFromUpsert(any(), any());
        verify(repository).save(entity);
    }

    @Test
    void shouldFindByModelVersionKey() {
        final TcModelMdfJpaStore store = new TcModelMdfJpaStore(repository, mapper);

        final TcModelMdfEntity entity = new TcModelMdfEntity(1L, 200L, "MDF_MAIN", "<mdf/>".getBytes());
        final TcModelMdf domain = new TcModelMdf(1L, 200L, "MDF_MAIN", "<mdf/>".getBytes(), OffsetDateTime.now());

        when(repository.findByModelVersionKey(200L)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        final Optional<TcModelMdf> found = store.findByModelVersionKey(200L);
        Assertions.assertTrue(found.isPresent());
        Assertions.assertEquals(200L, found.orElseThrow().modelVersionKey());
    }
}
