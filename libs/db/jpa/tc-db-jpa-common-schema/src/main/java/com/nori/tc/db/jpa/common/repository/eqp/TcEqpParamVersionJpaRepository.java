package com.nori.tc.db.jpa.common.repository.eqp;

import com.nori.tc.db.jpa.common.entity.eqp.TcEqpParamVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * tc_eqp_param_version Repository입니다.
 */
public interface TcEqpParamVersionJpaRepository extends JpaRepository<TcEqpParamVersionEntity, Long> {

    Optional<TcEqpParamVersionEntity> findByEqpKeyAndParamVersion(long eqpKey, String paramVersion);

    @Modifying
    @Query("DELETE FROM TcEqpParamVersionEntity e WHERE e.eqpKey = :eqpKey")
    int deleteAllByEqpKey(@Param("eqpKey") long eqpKey);
}
