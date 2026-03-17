package com.nori.tc.db.core.model.upsert;

/**
 * tc_model_mes_message upsert 입력(Command).
 *
 * <p>
 * - mes_msg_key가 있으면 해당 PK 기반으로 갱신합니다.
 * - mes_msg_key가 없으면 (model_version_key, mes_msg_name) 유니크 키 기준으로
 * 존재 여부를 확인한 뒤 갱신/생성을 수행합니다.
 * </p>
 *
 * <p>
 * 주의:
 * - updated_at은 DB(또는 구현체)에서 현재 시각으로 갱신되도록 처리하는 것을 권장합니다.
 * </p>
 */
public record UpsertTcModelMesMessage(
        Long mesMsgKey,
        long modelVersionKey,
        String mesMsgName,
        String description,
        String dataIndex
) {

    public UpsertTcModelMesMessage {
        ModelFieldLengthValidator.requireTextWithMax(
                mesMsgName,
                "mesMsgName",
                ModelFieldLengthValidator.MES_MSG_NAME_MAX_LENGTH
        );
    }
}
