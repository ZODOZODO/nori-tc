package com.nori.tc.apps.uibackend.scenario;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.common.user.TcUiPermissionMatchType;
import com.nori.tc.db.domain.common.user.TcUiPermissionResourceType;
import com.nori.tc.db.domain.common.user.UserStatus;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.user.TcUiPermission;
import com.nori.tc.db.domain.user.TcUserGroup;
import com.nori.tc.db.domain.user.TcUserGroupMember;
import com.nori.tc.db.domain.user.TcUserGroupPermission;
import com.nori.tc.db.domain.user.TcUserInfo;
import com.nori.tc.ui.core.exception.UiBadRequestException;
import com.nori.tc.ui.core.exception.UiConflictException;
import com.nori.tc.ui.core.model.PagedResponse;
import com.nori.tc.ui.domain.auth.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 시나리오 검증: UI 관리 페이지 확장 API의 통합 시나리오 테스트입니다.
 *
 * <p>검증 범위:</p>
 * <ul>
 *   <li>인증/인가 기본 정책(401/403)</li>
 *   <li>로그인 후 EqpInfo 기본 진입 흐름(= GET /api/eqp 접근 가능)</li>
 *   <li>GET /api/eqp와 기존 명령 API(start) 공존</li>
 *   <li>Model/User/Group/Permission CRUD 및 매핑/비밀번호 초기화 시나리오</li>
 *   <li>409/400 예외 매핑 정책</li>
 *   <li>목록 API offset/limit 기본값/상한 정책</li>
 *   <li>권한 캐시 재로딩 반영</li>
 * </ul>
 */
@DisplayName("Phase 5: UI 관리 페이지 확장 시나리오 검증")
class UiManagementPagesScenarioTest extends UiBackendScenarioTestSupport {

    private static final Logger log = LoggerFactory.getLogger(UiManagementPagesScenarioTest.class);

    /** 모델 조회 권한 코드 */
    private static final String MODEL_READ_PERM = "MODEL_READ";

    /** 모델 변경 권한 코드 */
    private static final String MODEL_WRITE_PERM = "MODEL_WRITE";

    /** 사용자 관리 권한 코드 */
    private static final String USER_INFO_WRITE_PERM = "USER_INFO_WRITE";

    /** 그룹 관리 권한 코드 */
    private static final String GROUP_WRITE_PERM = "GROUP_WRITE";

    /** UI 권한 관리 권한 코드 */
    private static final String PERMISSION_WRITE_PERM = "PERMISSION_WRITE";

    /** 테스트용 모델 버전 키 */
    private static final long TEST_MODEL_VERSION_KEY = 1001L;

    /** 테스트용 그룹 ID */
    private static final long TEST_GROUP_ID = 2001L;

    /** 테스트용 권한 ID */
    private static final long TEST_PERMISSION_ID = 3001L;

    /**
     * 관리 페이지 시나리오 공통 사전 설정입니다.
     *
     * <p>모든 관리 API는 인증 쿠키 기반 인증을 요구하므로, 기본적으로 TEST_TOKEN에
     * 관리자 수준 권한을 부여해 둡니다. 특정 테스트(403 검증 등)는 메서드 내부에서
     * stubbing/권한 캐시를 재정의합니다.</p>
     */
    @BeforeEach
    void setUpManagementPrincipal() {
        final UserPrincipal adminPrincipal = principalWithPermission(
                EQP_MANAGE_PERM,
                MODEL_READ_PERM,
                MODEL_WRITE_PERM,
                USER_INFO_WRITE_PERM,
                GROUP_WRITE_PERM,
                PERMISSION_WRITE_PERM
        );
        lenient().when(tokenCachePort.get(anyString())).thenReturn(Optional.of(adminPrincipal));

        // Phase 5 시나리오에서 사용할 API 권한 매핑 기본값입니다.
        reloadPermissions(List.of(
                apiPermission(EQP_MANAGE_PERM, "/api/eqp", null),
                apiPermission(MODEL_READ_PERM, "/api/model", "GET"),
                apiPermission(MODEL_WRITE_PERM, "/api/model", "POST"),
                apiPermission(MODEL_WRITE_PERM, "/api/model", "PUT"),
                apiPermission(MODEL_WRITE_PERM, "/api/model", "DELETE"),
                apiPermission(USER_INFO_WRITE_PERM, "/api/user", null),
                apiPermission(GROUP_WRITE_PERM, "/api/group", null),
                apiPermission(PERMISSION_WRITE_PERM, "/api/permission", null)
        ));
    }

    /**
     * 인증이 없는 요청은 보호 API 접근 시 401이어야 합니다.
     */
    @Test
    @DisplayName("시나리오 5-1: 미인증 /api/model 요청 → 401")
    void 미인증_요청_401() throws Exception {
        log.info("[Phase5-1] 미인증 요청 401 검증 시작");

        mockMvc.perform(get("/api/model"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[Phase5-1] 401 응답 확인 완료");
    }

    /**
     * 인증은 되었지만 권한이 부족하면 403으로 차단되어야 합니다.
     */
    @Test
    @DisplayName("시나리오 5-2: 인증됨 + MODEL_WRITE 권한 없음 /api/model POST → 403")
    void 권한_부족_403() throws Exception {
        log.info("[Phase5-2] 권한 부족 403 검증 시작");

        reloadPermissions(List.of(
                apiPermission(MODEL_WRITE_PERM, "/api/model", "POST")
        ));
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principalWithNoPermission()));

        mockMvc.perform(post("/api/model")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelName":"MODEL-A",
                                  "modelVersion":"v1",
                                  "commInterface":"HSMS",
                                  "status":"ACTIVE",
                                  "maker":"NORI",
                                  "createdBy":"SYSTEM",
                                  "updatedBy":"SYSTEM"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isForbidden());

        log.info("[Phase5-2] 403 응답 확인 완료");
    }

    /**
     * 로그인 성공 후 기본 진입 페이지(EqpInfo)에서 사용하는 GET /api/eqp가 정상 동작해야 하며,
     * 기존 명령 API(start)도 계속 동작해야 합니다.
     */
    @Test
    @DisplayName("시나리오 5-3: 로그인 성공 + GET /api/eqp + POST /api/eqp/{id}/start 공존")
    void 로그인후_EqpInfo_진입과_기존_명령_API_공존_검증() throws Exception {
        log.info("[Phase5-3] 로그인 이후 EqpInfo 진입/명령 API 공존 검증 시작");

        // 로그인 성공 시나리오용 Mock 설정
        final String rawPassword = "password123";
        final String fakeHash = "$2a$10$phase5FakeHashValueForLoginFlow12345678901234567890123";
        when(userPort.findByUserIdNorm(TEST_USER_ID)).thenReturn(Optional.of(activeUserInfo(fakeHash)));
        when(passwordVerifierPort.matches(rawPassword, fakeHash)).thenReturn(true);

        // EqpInfo 기본 진입 화면에서 사용할 목록 데이터 준비
        when(eqpQueryPort.findAll(PageRequest.of(0, 100)))
                .thenReturn(PagedResponse.of(List.of(sampleEqp()), 0, 100, 1));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":"testuser",
                                  "password":"password123"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").doesNotExist());

        mockMvc.perform(get("/api/eqp")
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].eqpId").value(TEST_EQP_ID));

        mockMvc.perform(post("/api/eqp/{eqpId}/start", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "interfaceType":"HSMS"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.traceId").isNotEmpty());

        log.info("[Phase5-3] 로그인 이후 EqpInfo 진입/명령 API 공존 검증 완료");
    }

    /**
     * Model CRUD 정상 플로우(목록/등록/조회/수정/삭제)를 검증합니다.
     */
    @Test
    @DisplayName("시나리오 5-4: Model CRUD 정상 플로우")
    void 모델_CRUD_정상_플로우() throws Exception {
        log.info("[Phase5-4] Model CRUD 정상 플로우 검증 시작");

        final TcModel model = sampleModel();
        when(modelCrudPort.findAll(PageRequest.of(0, 100)))
                .thenReturn(PagedResponse.of(List.of(model), 0, 100, 1));
        when(modelCrudPort.findByModelVersionKey(TEST_MODEL_VERSION_KEY)).thenReturn(Optional.of(model));
        when(modelCrudPort.upsert(any())).thenReturn(model);

        mockMvc.perform(get("/api/model")
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offset").value(0))
                .andExpect(jsonPath("$.data.limit").value(100))
                .andExpect(jsonPath("$.data.count").value(1));
        verify(modelCrudPort).findAll(PageRequest.of(0, 100));

        mockMvc.perform(post("/api/model")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelName":"MODEL-PHASE5",
                                  "modelVersion":"v1",
                                  "commInterface":"HSMS",
                                  "status":"ACTIVE",
                                  "maker":"NORI",
                                  "createdBy":"SYSTEM",
                                  "updatedBy":"SYSTEM"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.modelVersionKey").value(TEST_MODEL_VERSION_KEY));

        mockMvc.perform(get("/api/model/{modelVersionKey}", TEST_MODEL_VERSION_KEY)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelVersionKey").value(TEST_MODEL_VERSION_KEY));

        mockMvc.perform(put("/api/model/{modelVersionKey}", TEST_MODEL_VERSION_KEY)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelName":"MODEL-PHASE5-UPDATED",
                                  "modelVersion":"v2",
                                  "commInterface":"SOCKET",
                                  "status":"DEPRECATED",
                                  "maker":"NORI",
                                  "createdBy":"SYSTEM",
                                  "updatedBy":"ADMIN"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/model/{modelVersionKey}", TEST_MODEL_VERSION_KEY)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        log.info("[Phase5-4] Model CRUD 정상 플로우 검증 완료");
    }

    /**
     * Model CRUD 실패 시나리오(중복/참조 충돌)에서 409 변환을 검증합니다.
     */
    @Test
    @DisplayName("시나리오 5-5: Model 중복/참조충돌 → 409")
    void 모델_중복_참조충돌_409() throws Exception {
        log.info("[Phase5-5] Model 중복/참조충돌 409 검증 시작");

        when(modelCrudPort.upsert(any()))
                .thenThrow(new UiConflictException("중복 모델 버전입니다."));

        mockMvc.perform(post("/api/model")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelName":"MODEL-DUP",
                                  "modelVersion":"v1",
                                  "commInterface":"HSMS",
                                  "status":"ACTIVE",
                                  "maker":"NORI"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));

        doThrow(new UiConflictException("참조 중인 설비가 존재합니다."))
                .when(modelCrudPort).deleteByModelVersionKey(anyLong());

        mockMvc.perform(delete("/api/model/{modelVersionKey}", TEST_MODEL_VERSION_KEY)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));

        log.info("[Phase5-5] Model 중복/참조충돌 409 검증 완료");
    }

    /**
     * User CRUD + 비밀번호 초기화 + 사용자-그룹 매핑 CRUD를 검증합니다.
     */
    @Test
    @DisplayName("시나리오 5-6: User CRUD + password reset + user-group mapping CRUD")
    void 사용자_관리_전체_플로우() throws Exception {
        log.info("[Phase5-6] User CRUD/비밀번호초기화/사용자-그룹매핑 검증 시작");

        final TcUserInfo user = sampleUser();
        final TcUserGroupMember mapping = sampleUserGroupMapping();

        when(userCrudPort.findAll(PageRequest.of(0, 100)))
                .thenReturn(PagedResponse.of(List.of(user), 0, 100, 1));
        when(userCrudPort.findByUserPk(TEST_USER_PK)).thenReturn(Optional.of(user));
        when(userCrudPort.upsert(any())).thenReturn(user);
        when(userGroupMappingPort.upsert(any())).thenReturn(mapping);

        mockMvc.perform(get("/api/user")
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(post("/api/user")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company":"NORI",
                                  "department":"개발팀",
                                  "userName":"테스트유저",
                                  "userId":"phase5.user",
                                  "password":"Passw0rd!",
                                  "email":"phase5.user@nori.com",
                                  "status":"ACTIVE",
                                  "createdBy":"SYSTEM",
                                  "updatedBy":"SYSTEM"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/user/{userPk}", TEST_USER_PK)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userPk").value(TEST_USER_PK));

        mockMvc.perform(put("/api/user/{userPk}", TEST_USER_PK)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company":"NORI",
                                  "department":"품질팀",
                                  "userName":"테스트유저-수정",
                                  "userId":"phase5.user",
                                  "email":"phase5.user@nori.com",
                                  "status":"LOCKED",
                                  "createdBy":"SYSTEM",
                                  "updatedBy":"ADMIN"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/user/{userPk}/password/reset", TEST_USER_PK)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword":"ResetPassw0rd!",
                                  "updatedBy":"ADMIN"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/user/{userPk}/group/{groupId}", TEST_USER_PK, TEST_GROUP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userPk").value(TEST_USER_PK))
                .andExpect(jsonPath("$.data.groupId").value(TEST_GROUP_ID));

        mockMvc.perform(delete("/api/user/{userPk}/group/{groupId}", TEST_USER_PK, TEST_GROUP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/user/{userPk}", TEST_USER_PK)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        log.info("[Phase5-6] User 관리 전체 플로우 검증 완료");
    }

    /**
     * Group CRUD + 그룹-권한 매핑 CRUD를 검증합니다.
     */
    @Test
    @DisplayName("시나리오 5-7: Group CRUD + group-permission mapping CRUD")
    void 그룹_관리_전체_플로우() throws Exception {
        log.info("[Phase5-7] Group CRUD/그룹-권한매핑 검증 시작");

        final TcUserGroup group = sampleGroup();
        final TcUserGroupPermission mapping = sampleGroupPermissionMapping();

        when(groupCrudPort.findAll(PageRequest.of(0, 100)))
                .thenReturn(PagedResponse.of(List.of(group), 0, 100, 1));
        when(groupCrudPort.findByGroupId(TEST_GROUP_ID)).thenReturn(Optional.of(group));
        when(groupCrudPort.upsert(any())).thenReturn(group);
        when(groupPermissionMappingPort.findAllByGroupId(TEST_GROUP_ID, PageRequest.of(0, 100)))
                .thenReturn(PagedResponse.of(List.of(mapping), 0, 100, 1));
        when(groupPermissionMappingPort.upsert(any())).thenReturn(mapping);

        mockMvc.perform(get("/api/group")
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(post("/api/group")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupCode":"PHASE5_GROUP",
                                  "groupName":"Phase5 그룹",
                                  "description":"시나리오 테스트 그룹",
                                  "isActive":true
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/group/{groupId}", TEST_GROUP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(TEST_GROUP_ID));

        mockMvc.perform(put("/api/group/{groupId}", TEST_GROUP_ID)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupCode":"PHASE5_GROUP",
                                  "groupName":"Phase5 그룹 수정",
                                  "description":"수정 설명",
                                  "isActive":false
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/group/{groupId}/permission", TEST_GROUP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(post("/api/group/{groupId}/permission/{permId}", TEST_GROUP_ID, TEST_PERMISSION_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(TEST_GROUP_ID))
                .andExpect(jsonPath("$.data.permId").value(TEST_PERMISSION_ID));

        mockMvc.perform(delete("/api/group/{groupId}/permission/{permId}", TEST_GROUP_ID, TEST_PERMISSION_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/group/{groupId}", TEST_GROUP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        log.info("[Phase5-7] Group 관리 전체 플로우 검증 완료");
    }

    /**
     * Permission CRUD와 권한 캐시 재로딩 반영 여부를 함께 검증합니다.
     */
    @Test
    @DisplayName("시나리오 5-8: Permission CRUD + 권한 캐시 재로딩 반영")
    void 권한_CRUD_및_권한캐시_재로딩_검증() throws Exception {
        log.info("[Phase5-8] Permission CRUD/권한 캐시 재로딩 검증 시작");

        final TcUiPermission permission = samplePermission();
        when(permissionCrudPort.findAll(PageRequest.of(0, 100)))
                .thenReturn(PagedResponse.of(List.of(permission), 0, 100, 1));
        when(permissionCrudPort.findByPermId(TEST_PERMISSION_ID)).thenReturn(Optional.of(permission));
        when(permissionCrudPort.upsert(any())).thenReturn(permission);

        mockMvc.perform(get("/api/permission")
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(post("/api/permission")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permCode":"PHASE5_PERMISSION_WRITE",
                                  "permName":"Phase5 Permission Write",
                                  "resourceType":"API",
                                  "matchType":"PREFIX",
                                  "resource":"/api/permission",
                                  "httpMethod":"POST",
                                  "description":"phase5 생성 권한",
                                  "isActive":true,
                                  "createdBy":"SYSTEM",
                                  "updatedBy":"SYSTEM"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/permission/{permId}", TEST_PERMISSION_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permId").value(TEST_PERMISSION_ID));

        mockMvc.perform(put("/api/permission/{permId}", TEST_PERMISSION_ID)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permCode":"PHASE5_PERMISSION_WRITE",
                                  "permName":"Phase5 Permission Write Updated",
                                  "resourceType":"API",
                                  "matchType":"PREFIX",
                                  "resource":"/api/permission",
                                  "httpMethod":"PUT",
                                  "description":"phase5 수정 권한",
                                  "isActive":true,
                                  "createdBy":"SYSTEM",
                                  "updatedBy":"ADMIN"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/permission/{permId}", TEST_PERMISSION_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 권한 캐시 재로딩: 동일 사용자라도 요구 permCode가 바뀌면 즉시 차단되어야 합니다.
        reloadPermissions(List.of(
                apiPermission("PERMISSION_ADMIN_ONLY", "/api/permission", "GET")
        ));

        mockMvc.perform(get("/api/permission")
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isForbidden());

        log.info("[Phase5-8] Permission CRUD/권한 캐시 재로딩 검증 완료");
    }

    /**
     * 물리 삭제 시 FK/입력 제약 예외가 409/400으로 매핑되는지 검증합니다.
     */
    @Test
    @DisplayName("시나리오 5-9: 물리 삭제 충돌(409) + 입력 오류(400) 매핑")
    void 물리삭제_충돌_에러코드_검증() throws Exception {
        log.info("[Phase5-9] 물리 삭제 충돌 409/400 매핑 검증 시작");

        doThrow(new UiConflictException("그룹 삭제 충돌"))
                .when(groupCrudPort).deleteByGroupId(TEST_GROUP_ID);
        mockMvc.perform(delete("/api/group/{groupId}", TEST_GROUP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));

        doThrow(new UiBadRequestException("userPk는 1 이상이어야 합니다."))
                .when(userCrudPort).deleteByUserPk(TEST_USER_PK);
        mockMvc.perform(delete("/api/user/{userPk}", TEST_USER_PK)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        log.info("[Phase5-9] 물리 삭제 충돌 409/400 매핑 검증 완료");
    }

    /**
     * 목록 API의 limit 상한 정책(최대 500)을 검증합니다.
     */
    @Test
    @DisplayName("시나리오 5-10: 목록 API limit 상한(500) 초과 시 400")
    void 목록_API_limit_상한_검증() throws Exception {
        log.info("[Phase5-10] 목록 API limit 상한 정책 검증 시작");

        mockMvc.perform(get("/api/eqp")
                        .cookie(authCookie())
                        .with(csrf())
                        .queryParam("limit", "501"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/model")
                        .cookie(authCookie())
                        .with(csrf())
                        .queryParam("limit", "501"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/user")
                        .cookie(authCookie())
                        .with(csrf())
                        .queryParam("limit", "501"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/group")
                        .cookie(authCookie())
                        .with(csrf())
                        .queryParam("limit", "501"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/permission")
                        .cookie(authCookie())
                        .with(csrf())
                        .queryParam("limit", "501"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        log.info("[Phase5-10] 목록 API limit 상한 정책 검증 완료");
    }

    /**
     * 설비 목록 응답 픽스처를 생성합니다.
     *
     * @return 테스트용 TcEqp
     */
    private static TcEqp sampleEqp() {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcEqp(
                10L,
                TEST_EQP_ID,
                ProtocolType.HSMS,
                "ACTIVE",
                1,
                "127.0.0.1",
                5000,
                TEST_MODEL_VERSION_KEY,
                true,
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    /**
     * 모델 응답 픽스처를 생성합니다.
     *
     * @return 테스트용 TcModel
     */
    private static TcModel sampleModel() {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcModel(
                TEST_MODEL_VERSION_KEY,
                501L,
                "MODEL-PHASE5",
                "v1",
                ProtocolType.HSMS,
                ModelStatus.ACTIVE,
                "NORI",
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    /**
     * 사용자 응답 픽스처를 생성합니다.
     *
     * @return 테스트용 TcUserInfo
     */
    private static TcUserInfo sampleUser() {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcUserInfo(
                TEST_USER_PK,
                "NORI",
                "개발팀",
                "테스트유저",
                "phase5.user",
                "phase5.user",
                "$2a$10$sampleHashForPhase5Scenario012345678901234567890123456789",
                "phase5.user@nori.com",
                UserStatus.ACTIVE,
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    /**
     * 사용자-그룹 매핑 응답 픽스처를 생성합니다.
     *
     * @return 테스트용 TcUserGroupMember
     */
    private static TcUserGroupMember sampleUserGroupMapping() {
        return new TcUserGroupMember(
                7001L,
                TEST_USER_PK,
                TEST_GROUP_ID,
                OffsetDateTime.now(),
                "SYSTEM"
        );
    }

    /**
     * 그룹 응답 픽스처를 생성합니다.
     *
     * @return 테스트용 TcUserGroup
     */
    private static TcUserGroup sampleGroup() {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcUserGroup(
                TEST_GROUP_ID,
                "PHASE5_GROUP",
                "Phase5 그룹",
                "시나리오 테스트 그룹",
                true,
                now,
                now
        );
    }

    /**
     * 그룹-권한 매핑 응답 픽스처를 생성합니다.
     *
     * @return 테스트용 TcUserGroupPermission
     */
    private static TcUserGroupPermission sampleGroupPermissionMapping() {
        return new TcUserGroupPermission(
                8001L,
                TEST_GROUP_ID,
                TEST_PERMISSION_ID,
                OffsetDateTime.now(),
                "SYSTEM"
        );
    }

    /**
     * UI 권한 응답 픽스처를 생성합니다.
     *
     * @return 테스트용 TcUiPermission
     */
    private static TcUiPermission samplePermission() {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcUiPermission(
                TEST_PERMISSION_ID,
                "PHASE5_PERMISSION_WRITE",
                "Phase5 Permission Write",
                TcUiPermissionResourceType.API,
                TcUiPermissionMatchType.PREFIX,
                "/api/permission",
                "GET",
                "phase5 권한",
                true,
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }
}
