package com.nori.tc.db.jpa.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * tc_eqp_socket_protocol_type 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - socket_protocol_type      : varchar(32) PK
 * - socket_protocol_type_name : varchar(100) NOT NULL
 * - parse_start_rule          : varchar(1000)
 * - parse_end_rule            : varchar(1000)
 * - parse_regex               : varchar(1000)
 * - description               : varchar(1000)
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 생성자를 제공하여 Mapper가 객체를 생성할 수 있게 합니다.
 *
 * 2. 컬럼 길이/Nullable 명시:
 * - JPA 스키마 매핑과 DB DDL의 일치를 유지합니다.
 */
@Entity
@Table(name = "tc_eqp_socket_protocol_type")
public class TcEqpSocketProtocolTypeEntity {

    @Id
    @Column(name = "socket_protocol_type", length = 32, nullable = false)
    private String socketProtocolType;

    @Column(name = "socket_protocol_type_name", length = 100, nullable = false)
    private String socketProtocolTypeName;

    @Column(name = "parse_start_rule", length = 1000)
    private String parseStartRule;

    @Column(name = "parse_end_rule", length = 1000)
    private String parseEndRule;

    @Column(name = "parse_regex", length = 1000)
    private String parseRegex;

    @Column(name = "description", length = 1000)
    private String description;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcEqpSocketProtocolTypeEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcEqpSocketProtocolTypeEntity(String socketProtocolType, String socketProtocolTypeName,
                                         String parseStartRule, String parseEndRule, String parseRegex,
                                         String description) {
        this.socketProtocolType = socketProtocolType;
        this.socketProtocolTypeName = socketProtocolTypeName;
        this.parseStartRule = parseStartRule;
        this.parseEndRule = parseEndRule;
        this.parseRegex = parseRegex;
        this.description = description;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    /**
     * 신규 엔티티 생성 팩토리
     * - Store 계층의 upsert 로직에서 신규 row가 필요할 때 사용합니다.
     */
    public static TcEqpSocketProtocolTypeEntity newEntity(String socketProtocolType) {
        if (socketProtocolType == null || socketProtocolType.isBlank()) {
            throw new IllegalArgumentException("socketProtocolType must not be null/blank");
        }
        TcEqpSocketProtocolTypeEntity e = new TcEqpSocketProtocolTypeEntity();
        e.setSocketProtocolType(socketProtocolType);
        return e;
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public String getSocketProtocolType() {
        return socketProtocolType;
    }

    public void setSocketProtocolType(String socketProtocolType) {
        this.socketProtocolType = socketProtocolType;
    }

    public String getSocketProtocolTypeName() {
        return socketProtocolTypeName;
    }

    public void setSocketProtocolTypeName(String socketProtocolTypeName) {
        this.socketProtocolTypeName = socketProtocolTypeName;
    }

    public String getParseStartRule() {
        return parseStartRule;
    }

    public void setParseStartRule(String parseStartRule) {
        this.parseStartRule = parseStartRule;
    }

    public String getParseEndRule() {
        return parseEndRule;
    }

    public void setParseEndRule(String parseEndRule) {
        this.parseEndRule = parseEndRule;
    }

    public String getParseRegex() {
        return parseRegex;
    }

    public void setParseRegex(String parseRegex) {
        this.parseRegex = parseRegex;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
