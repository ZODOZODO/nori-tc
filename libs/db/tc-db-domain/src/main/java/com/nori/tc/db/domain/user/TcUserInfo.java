package com.nori.tc.db.domain.user;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.user.UserStatus;

/**
 * tc_user_info 테이블 1행에 대응하는 순수 DTO.
 *
 * [DB 스키마 요약]
 * - user_pk       : bigint PK (IDENTITY)
 * - company       : varchar(100) (NOT NULL)
 * - department    : varchar(100) (NOT NULL)
 * - user_name     : varchar(100) (NOT NULL)
 * - user_id       : varchar(50) (NOT NULL)
 * - user_id_norm  : varchar(50) (NOT NULL, UNIQUE)
 * - password_hash : varchar(255) (NOT NULL)
 * - email         : varchar(255) (NOT NULL, UNIQUE)
 * - status        : varchar(20) (ACTIVE/LOCKED/DISABLED/DELETED)
 * - created_at    : timestamptz (DEFAULT CURRENT_TIMESTAMP)
 * - updated_at    : timestamptz (DEFAULT CURRENT_TIMESTAMP)
 * - created_by    : varchar(50) (DEFAULT SYSTEM)
 * - updated_by    : varchar(50) (DEFAULT SYSTEM)
 */
public record TcUserInfo(
        Long userPk,
        String company,
        String department,
        String userName,
        String userId,
        String userIdNorm,
        String passwordHash,
        String email,
        UserStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
