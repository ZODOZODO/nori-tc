package com.nori.tc.db.jpa.common.entity;

import com.nori.tc.db.jpa.common.entity.eqp.TcEqpEntity;
import com.nori.tc.db.jpa.common.entity.model.TcModelDcopItemEntity;
import com.nori.tc.db.jpa.common.entity.model.TcModelEntity;
import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;
import com.nori.tc.db.jpa.common.entity.model.TcModelParamEntity;
import com.nori.tc.db.jpa.common.entity.model.TcModelSecsMessageEntity;
import com.nori.tc.db.jpa.common.entity.model.TcModelSocketMessageEntity;
import com.nori.tc.db.jpa.common.entity.model.TcModelVariableIdEntity;
import com.nori.tc.db.jpa.common.entity.model.TcModelWorkflowEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DDL과 JPA 엔티티의 문자열 길이 계약이 어긋나지 않는지 검증합니다.
 */
class EntityColumnLengthContractTest {

    @Test
    @DisplayName("EQP 엔티티는 applied_param_version 100자 계약을 유지합니다")
    void eqpEntityKeepsAppliedParamVersionLength() throws Exception {
        assertColumnLength(TcEqpEntity.class, "appliedParamVersion", 100);
    }

    @Test
    @DisplayName("모델 엔티티는 model_name/parent_model 1000자 계약을 유지합니다")
    void modelEntityKeepsExtendedNameLengths() throws Exception {
        assertColumnLength(TcModelEntity.class, "modelName", 1000);
        assertColumnLength(TcModelEntity.class, "parentModel", 1000);
    }

    @Test
    @DisplayName("workflow 엔티티는 확장된 문자열 길이 계약을 유지합니다")
    void workflowEntityKeepsExtendedFieldLengths() throws Exception {
        assertColumnLength(TcModelWorkflowEntity.class, "workflowName", 1000);
        assertColumnLength(TcModelWorkflowEntity.class, "messageName", 1000);
        assertColumnLength(TcModelWorkflowEntity.class, "transactionId", 2000);
        assertColumnLength(TcModelWorkflowEntity.class, "workflowFilter", 4000);
    }

    @Test
    @DisplayName("dcop 엔티티는 확장된 문자열 길이 계약을 유지합니다")
    void dcopEntityKeepsExtendedFieldLengths() throws Exception {
        assertColumnLength(TcModelDcopItemEntity.class, "dcopItemName", 1000);
        assertColumnLength(TcModelDcopItemEntity.class, "workflowName", 1000);
        assertColumnLength(TcModelDcopItemEntity.class, "variableId", 1000);
        assertColumnLength(TcModelDcopItemEntity.class, "calculationRule", 2000);
    }

    @Test
    @DisplayName("상세 엔티티는 확장된 문자열 길이 계약을 유지합니다")
    void detailEntitiesKeepExtendedFieldLengths() throws Exception {
        assertColumnLength(TcModelMdfEntity.class, "mdfName", 1000);
        assertColumnLength(TcModelParamEntity.class, "paramName", 1000);
        assertColumnLength(TcModelSecsMessageEntity.class, "secsMsgName", 1000);
        assertColumnLength(TcModelSocketMessageEntity.class, "socketMsgName", 1000);
        assertColumnLength(TcModelVariableIdEntity.class, "variableId", 1000);
    }

    private static void assertColumnLength(
            final Class<?> entityType,
            final String fieldName,
            final int expectedLength
    ) throws Exception {
        final Field field = entityType.getDeclaredField(fieldName);
        final Column column = field.getAnnotation(Column.class);

        assertNotNull(column, () -> entityType.getSimpleName() + "." + fieldName + " 에 @Column이 없습니다.");
        assertEquals(expectedLength, column.length(), () -> entityType.getSimpleName() + "." + fieldName + " length 불일치");
    }
}
