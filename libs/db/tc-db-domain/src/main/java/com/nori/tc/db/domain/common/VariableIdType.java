package com.nori.tc.db.domain.common;

/**
 * 변수 ID 타입 (tc_model_variableid.variable_id_type)
 *
 * DB Check Constraint:
 * - SVID
 * - DVID
 * - ECID
 * - CEID
 */
public enum VariableIdType {
    SVID,
    DVID,
    ECID,
    CEID
}
