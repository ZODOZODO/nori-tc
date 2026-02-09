package com.nori.tc.apps.commgateway.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Gateway 설비 마스터 테이블
 *
 * - 통신 타입(HSMS/SOCKET)과 socketType, HSMS deviceId 등
 *   런타임 컨텍스트 생성에 필요한 최소 정보를 보관합니다.
 */
@Entity
@Table(name = "tc_gateway_equipment")
public class GatewayEquipmentEntity {

    @Id
    @Column(name = "eqp_id", nullable = false, length = 100)
    private String equipmentId;

    @Column(name = "comm_interface_type", nullable = false, length = 20)
    private String commInterfaceType;

    @Column(name = "socket_type", length = 50)
    private String socketType;

    @Column(name = "hsms_device_id")
    private Integer hsmsDeviceId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version")
    private Long version;

    protected GatewayEquipmentEntity() {
        // JPA 전용 기본 생성자
    }

    public GatewayEquipmentEntity(
            final String equipmentId,
            final String commInterfaceType,
            final String socketType,
            final Integer hsmsDeviceId,
            final boolean enabled,
            final String description
    ) {
        this.equipmentId = equipmentId;
        this.commInterfaceType = commInterfaceType;
        this.socketType = socketType;
        this.hsmsDeviceId = hsmsDeviceId;
        this.enabled = enabled;
        this.description = description;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(final String equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getCommInterfaceType() {
        return commInterfaceType;
    }

    public void setCommInterfaceType(final String commInterfaceType) {
        this.commInterfaceType = commInterfaceType;
    }

    public String getSocketType() {
        return socketType;
    }

    public void setSocketType(final String socketType) {
        this.socketType = socketType;
    }

    public Integer getHsmsDeviceId() {
        return hsmsDeviceId;
    }

    public void setHsmsDeviceId(final Integer hsmsDeviceId) {
        this.hsmsDeviceId = hsmsDeviceId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(final Long version) {
        this.version = version;
    }
}
