package com.nori.tc.db.mybatis.common.store.user;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.user.store.TcUserInfoStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUserInfo;
import com.nori.tc.db.domain.common.user.UserStatus;
import com.nori.tc.db.domain.user.TcUserInfo;
import com.nori.tc.db.mybatis.common.mapper.user.TcUserInfoMapper;

/**
 * tc_user_info MyBatis Store 구현체.
 *
 * 목표
 * - app은 TcUserInfoStore(Port)만 알고 CRUD를 수행한다.
 * - 실제 DB 접근(MyBatis Mapper 호출)은 이 구현체가 담당한다.
 *
 * 업서트 전략(벤더 중립)
 * - user_pk가 있으면 updateByUserPk 먼저 시도
 * - user_pk가 없으면 user_id_norm 기준 updateByUserIdNorm 먼저 시도
 * - update 영향 0이면 insert 시도
 * - insert가 UNIQUE 중복이면 update로 수렴
 */
@Repository
public class TcUserInfoMybatisStore implements TcUserInfoStore {

    private final TcUserInfoMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcUserInfoMybatisStore(TcUserInfoMapper mapper) {
        this.mapper = mapper;
    }

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    @Transactional
    public TcUserInfo upsert(UpsertTcUserInfo command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        UpsertTcUserInfo normalized = normalizeCommand(command);
        validateCommand(normalized);

        final Long userPk = normalized.userPk();
        final String userIdNorm = normalized.userIdNorm();

        final TcUserInfo row = new TcUserInfo(
                userPk,
                normalized.company(),
                normalized.department(),
                normalized.userName(),
                normalized.userId(),
                userIdNorm,
                normalized.passwordHash(),
                normalized.email(),
                normalized.status(),
                null,
                null,
                normalized.createdBy(),
                normalized.updatedBy()
        );

        try {
            int updated;
            if (userPk != null && userPk > 0) {
                updated = mapper.updateByUserPk(row);
            } else {
                updated = mapper.updateByUserIdNorm(row);
            }

            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.updateByUserIdNorm(row);
                }
            }

            return mapper.findByUserIdNorm(userIdNorm)
                    .orElseThrow(() -> new DbAccessException("tc_user_info upsert succeeded but row not found. userIdNorm=" + userIdNorm));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_user_info upsert duplicate key. userIdNorm=" + userIdNorm, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_info upsert failed. userIdNorm=" + userIdNorm, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_info upsert failed (unexpected). userIdNorm=" + userIdNorm, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserInfo> findByUserPk(long userPk) {
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be positive");
        }
        try {
            return mapper.findByUserPk(userPk);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_info findByUserPk failed. userPk=" + userPk, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_info findByUserPk failed (unexpected). userPk=" + userPk, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userIdNorm DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserInfo> findByUserIdNorm(String userIdNorm) {
        if (userIdNorm == null || userIdNorm.isBlank()) {
            throw new IllegalArgumentException("userIdNorm must not be blank");
        }
        try {
            return mapper.findByUserIdNorm(userIdNorm);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_info findByUserIdNorm failed. userIdNorm=" + userIdNorm, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_info findByUserIdNorm failed (unexpected). userIdNorm=" + userIdNorm, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param email DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserInfo> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        try {
            return mapper.findByEmail(email);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_info findByEmail failed. email=" + email, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_info findByEmail failed (unexpected). email=" + email, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcUserInfo> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAll(p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_info findAll failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_info findAll failed (unexpected).", e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param company DB MyBatis 계층 처리에 사용하는 입력 값
     * @param department DB MyBatis 계층 처리에 사용하는 입력 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcUserInfo> findAllByCompanyDepartment(String company, String department, PageRequest page) {
        if (company == null || company.isBlank()) {
            throw new IllegalArgumentException("company must not be blank");
        }
        if (department == null || department.isBlank()) {
            throw new IllegalArgumentException("department must not be blank");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByCompanyDepartment(company, department, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_info findAllByCompanyDepartment failed. company/department=" + company + "/" + department, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_info findAllByCompanyDepartment failed (unexpected). company/department=" + company + "/" + department, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByUserPk(long userPk) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be positive");
        }
        try {
            mapper.deleteByUserPk(userPk);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_info deleteByUserPk failed. userPk=" + userPk, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_info deleteByUserPk failed (unexpected). userPk=" + userPk, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcUserInfo command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.company() == null || command.company().isBlank()) {
            throw new IllegalArgumentException("command.company must not be blank");
        }
        if (command.department() == null || command.department().isBlank()) {
            throw new IllegalArgumentException("command.department must not be blank");
        }
        if (command.userName() == null || command.userName().isBlank()) {
            throw new IllegalArgumentException("command.userName must not be blank");
        }
        if (command.userId() == null || command.userId().isBlank()) {
            throw new IllegalArgumentException("command.userId must not be blank");
        }
        if (command.userIdNorm() == null || command.userIdNorm().isBlank()) {
            throw new IllegalArgumentException("command.userIdNorm must not be blank");
        }
        if (command.passwordHash() == null || command.passwordHash().isBlank()) {
            throw new IllegalArgumentException("command.passwordHash must not be blank");
        }
        if (command.email() == null || command.email().isBlank()) {
            throw new IllegalArgumentException("command.email must not be blank");
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private UpsertTcUserInfo normalizeCommand(UpsertTcUserInfo command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        UserStatus status = (command.status() == null) ? UserStatus.ACTIVE : command.status();
        return new UpsertTcUserInfo(
                command.userPk(),
                command.company(),
                command.department(),
                command.userName(),
                command.userId(),
                command.userIdNorm(),
                command.passwordHash(),
                command.email(),
                status,
                command.createdBy(),
                command.updatedBy()
        );
    }
}
