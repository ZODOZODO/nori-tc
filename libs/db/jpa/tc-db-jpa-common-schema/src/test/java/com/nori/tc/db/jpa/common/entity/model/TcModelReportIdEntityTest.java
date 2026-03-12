package com.nori.tc.db.jpa.common.entity.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TcModelReportIdEntityTest {

    @Test
    @DisplayName("신규 report id 엔티티 생성 시 updatedAt과 enabled 기본값을 함께 채웁니다")
    void onCreateInitializesUpdatedAtAndEnabled() {
        final TcModelReportIdEntity entity = TcModelReportIdEntity.newEntity(11L, "RPT_01");

        entity.onCreate();

        assertNotNull(entity.getUpdatedAt());
        assertEquals(Boolean.FALSE, entity.getEnabled());
        assertFalse(entity.getEnabled());
    }
}
