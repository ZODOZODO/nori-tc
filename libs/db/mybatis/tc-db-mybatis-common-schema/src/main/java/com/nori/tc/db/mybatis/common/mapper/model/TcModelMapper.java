package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModel;

/**
 * tc_model Mapper (FIX)
 *
 * - 이 모듈은 "보수적인 CRUD"만 제공한다.
 * - model_key 생성(IDENTITY) 처리 때문에 insert 후 key를 직접 반환하지 않는다.
 * → insert 후 findByNameVersion으로 재조회하는 방식(벤더 중립)을 권장한다.
 * - [2024-XX-XX FIX] 메모리 페이징 이슈 해결을 위해 findAll에 DB 페징 파라미터 추가
 */
public interface TcModelMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param model 도메인 데이터 객체
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("m") TcModel model);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param model 도메인 데이터 객체
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("m") TcModel model);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModel> findByModelVersionKey(@Param("modelVersionKey") long modelVersionKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelName 도메인 데이터 객체
     * @param modelVersion 도메인 데이터 객체
     * @return 조회 결과(Optional)
     */
    Optional<TcModel> findByNameVersion(
            @Param("modelName") String modelName,
            @Param("modelVersion") String modelVersion
    );

    /**
     * 목록 조회 (FIX: DB 페이징 적용)
     */
    List<TcModel> findAll(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByModelVersionKey(@Param("modelVersionKey") long modelVersionKey);

    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>모델 원장(tc_model) 1건을 삭제하며, FK ON DELETE CASCADE로 하위 버전/상세가 함께 삭제됩니다.</p>
     *
     * @param modelKey 대상 모델 키
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByModelKey(@Param("modelKey") long modelKey);
}
