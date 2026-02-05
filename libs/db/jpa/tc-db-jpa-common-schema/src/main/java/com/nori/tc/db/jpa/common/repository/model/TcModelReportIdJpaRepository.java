package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelReportIdEntity;

public interface TcModelReportIdJpaRepository extends JpaRepository<TcModelReportIdEntity, Long> {

    Optional<TcModelReportIdEntity> findByModelKeyAndReportId(long modelKey, String reportId);
}
