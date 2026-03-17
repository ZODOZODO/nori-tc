package com.nori.tc.db.jpa.common.repository.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelMesMessageEntity;

/**
 * tc_model_mes_message Repository
 */
public interface TcModelMesMessageJpaRepository extends JpaRepository<TcModelMesMessageEntity, Long> {

    /**
     * model_version_key로 MES 메시지 목록 조회.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 해당 모델 버전에 속한 MES 메시지 목록
     */
    List<TcModelMesMessageEntity> findByModelVersionKey(long modelVersionKey);

    /**
     * (model_version_key, mes_msg_name) 유니크 키로 단건 조회.
     *
     * @param modelVersionKey 모델 버전 키
     * @param mesMsgName      MES 메시지 이름
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMesMessageEntity> findByModelVersionKeyAndMesMsgName(long modelVersionKey, String mesMsgName);
}
