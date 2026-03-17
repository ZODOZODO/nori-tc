package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelMesMessage;

/**
 * tc_model_mes_message Mapper
 *
 * - model_version_key + mes_msg_name 유니크
 */
public interface TcModelMesMessageMapper {

    /**
     * MES 메시지 신규 삽입.
     *
     * @param message 삽입할 MES 메시지 도메인 객체
     * @return 영향 받은 행 수
     */
    int insert(@Param("m") TcModelMesMessage message);

    /**
     * MES 메시지 갱신 (PK 기반).
     *
     * @param message 갱신할 MES 메시지 도메인 객체
     * @return 영향 받은 행 수
     */
    int update(@Param("m") TcModelMesMessage message);

    /**
     * PK로 MES 메시지 단건 조회.
     *
     * @param mesMsgKey MES 메시지 PK
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMesMessage> findByMesMsgKey(@Param("mesMsgKey") long mesMsgKey);

    /**
     * (model_version_key, mes_msg_name) 유니크 키로 단건 조회.
     *
     * @param modelVersionKey 모델 버전 키
     * @param mesMsgName      MES 메시지 이름
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMesMessage> findByModelVersionKeyAndName(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("mesMsgName") String mesMsgName
    );

    /**
     * 특정 모델(model_version_key)의 MES 메시지 목록 조회 (페이징).
     *
     * @param modelVersionKey 모델 버전 키
     * @param offset          페이징 시작 오프셋
     * @param limit           페이징 최대 행 수
     * @return MES 메시지 목록
     */
    List<TcModelMesMessage> findAllByModelVersionKey(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * PK로 MES 메시지 삭제.
     *
     * @param mesMsgKey MES 메시지 PK
     * @return 영향 받은 행 수
     */
    int deleteByMesMsgKey(@Param("mesMsgKey") long mesMsgKey);
}
