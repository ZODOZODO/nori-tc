package com.nori.tc.db.jpa.common.entity.eqp;

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

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getSocketProtocolType() {
        return socketProtocolType;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     */
    public void setSocketProtocolType(String socketProtocolType) {
        this.socketProtocolType = socketProtocolType;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getSocketProtocolTypeName() {
        return socketProtocolTypeName;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolTypeName 통신 채널/세션 정보
     */
    public void setSocketProtocolTypeName(String socketProtocolTypeName) {
        this.socketProtocolTypeName = socketProtocolTypeName;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getParseStartRule() {
        return parseStartRule;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param parseStartRule DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setParseStartRule(String parseStartRule) {
        this.parseStartRule = parseStartRule;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getParseEndRule() {
        return parseEndRule;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param parseEndRule DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setParseEndRule(String parseEndRule) {
        this.parseEndRule = parseEndRule;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getParseRegex() {
        return parseRegex;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param parseRegex DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setParseRegex(String parseRegex) {
        this.parseRegex = parseRegex;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getDescription() {
        return description;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param description DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
