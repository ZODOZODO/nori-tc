package com.nori.tc.ui.adapter.db;

import com.nori.tc.db.core.eqp.store.TcEqpParamStore;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParam;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.ui.core.exception.EqpAlreadyCheckedOutException;
import com.nori.tc.ui.core.exception.UiBadRequestException;
import com.nori.tc.ui.core.port.db.EqpParamCommandPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link EqpParamCommandPort}의 DB Store 기반 구현체입니다.
 *
 * <p>체크아웃 잠금 메커니즘: {@code tc_eqp_param.param_version='EDIT'} 존재 여부 = 체크아웃 상태
 * {@code created_by} = 체크아웃한 사용자 ID (Spring Security에서 추출)</p>
 */
@Repository
public class JpaEqpParamCommandPort implements EqpParamCommandPort {

    private static final Logger log = LoggerFactory.getLogger(JpaEqpParamCommandPort.class);
    private static final String EDIT_VERSION = "EDIT";

    private final TcEqpStore eqpStore;
    private final TcEqpParamStore eqpParamStore;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param eqpStore tc_eqp Store 포트
     * @param eqpParamStore tc_eqp_param Store 포트
     */
    public JpaEqpParamCommandPort(
            final TcEqpStore eqpStore,
            final TcEqpParamStore eqpParamStore
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.eqpParamStore = Objects.requireNonNull(eqpParamStore, "eqpParamStore is null");
        log.info("JpaEqpParamCommandPort initialized.");
    }

    /**
     * 설비 파라미터를 체크아웃합니다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>eqpId로 eqpKey 확보 (설비 미존재 시 400)</li>
     *   <li>EDIT 버전 존재 시 {@link EqpAlreadyCheckedOutException} 발생 (409)</li>
     *   <li>sourceVersion 파라미터를 EDIT 버전으로 복사 (created_by = currentUser)</li>
     * </ol>
     *
     * @param eqpId 설비 비즈니스 ID
     * @param sourceVersion 복사 기준 버전
     * @param currentUser 체크아웃을 수행하는 사용자 ID
     * @return 생성된 EDIT 파라미터 목록
     */
    @Override
    @Transactional
    public List<EqpParamView> checkout(final String eqpId, final String sourceVersion, final String currentUser) {
        validateEqpId(eqpId);
        if (currentUser == null || currentUser.isBlank()) {
            throw new UiBadRequestException("currentUser는 비어 있을 수 없습니다.");
        }

        // sourceVersion이 null 또는 blank이면 파라미터 없는 빈 EDIT 버전으로 체크아웃
        final boolean hasSourceVersion = sourceVersion != null && !sourceVersion.isBlank();
        log.info("설비 파라미터 체크아웃 시작. eqpId={}, sourceVersion={}, currentUser={}", eqpId, hasSourceVersion ? sourceVersion : "(empty)", currentUser);

        // 같은 설비에 대한 동시 체크아웃은 tc_eqp 행 잠금으로 직렬화해 race 구간을 줄입니다.
        final TcEqp eqp = resolveEqpForCheckout(eqpId);
        final long eqpKey = eqp.eqpKey();

        // EDIT 버전이 이미 존재하면 체크아웃 중 → 409 Conflict
        if (eqpParamStore.existsByEqpKeyAndVersion(eqpKey, EDIT_VERSION)) {
            final String checkedOutBy = resolveCheckedOutBy(eqpKey);
            log.warn("설비 파라미터 이미 체크아웃 중. eqpId={}, eqpKey={}, sourceVersion={}, currentUser={}, checkedOutBy={}",
                    eqpId, eqpKey, hasSourceVersion ? sourceVersion : "(empty)", currentUser, checkedOutBy);
            throw new EqpAlreadyCheckedOutException(eqpId, checkedOutBy);
        }

        // sourceVersion이 있으면 해당 버전 파라미터를 EDIT으로 복사, 없으면 빈 EDIT 버전 생성
        final List<TcEqpParam> sourceParams = hasSourceVersion
                ? eqpParamStore.findAllByEqpKeyAndVersion(eqpKey, sourceVersion)
                : List.of();
        if (sourceParams.isEmpty()) {
            log.warn("체크아웃 기준 버전에 파라미터가 없습니다. eqpId={}, sourceVersion={}", eqpId, hasSourceVersion ? sourceVersion : "(empty)");
            // 빈 EDIT 버전 허용 (파라미터 없는 신규 설비 또는 빈 버전 체크아웃)
        }

        try {
            for (TcEqpParam sourceParam : sourceParams) {
                final UpsertTcEqpParam command = new UpsertTcEqpParam(
                        eqpKey,
                        sourceParam.paramName(),
                        EDIT_VERSION,
                        sourceParam.paramValue(),
                        sourceParam.description(),
                        currentUser
                );
                eqpParamStore.upsert(command);
            }
        } catch (DbDuplicateKeyException e) {
            // 설비 잠금 이후에도 기존 데이터 이상이나 교차 트랜잭션 타이밍으로 중복 키가 날 수 있다.
            // 이 경우에도 사용자에게는 동일한 409 메시지로 정규화해 노출한다.
            final String checkedOutBy = resolveCheckedOutBy(eqpKey);
            log.warn("설비 파라미터 체크아웃 중 중복 키 발생. eqpId={}, eqpKey={}, sourceVersion={}, currentUser={}, checkedOutBy={}",
                    eqpId, eqpKey, hasSourceVersion ? sourceVersion : "(empty)", currentUser, checkedOutBy, e);
            throw new EqpAlreadyCheckedOutException(eqpId, checkedOutBy);
        }

        final List<TcEqpParam> editParams = eqpParamStore.findAllByEqpKeyAndVersion(eqpKey, EDIT_VERSION);
        log.info("설비 파라미터 체크아웃 완료. eqpId={}, editParamCount={}", eqpId, editParams.size());

        return editParams.stream()
                .map(p -> new EqpParamView(p.paramName(), p.paramValue(), p.description(), p.createdBy()))
                .toList();
    }

    /**
     * EDIT 버전의 파라미터를 수정합니다.
     *
     * <p>EDIT 버전의 각 파라미터를 paramName 기준으로 upsert합니다.
     * created_by는 체크아웃 시 설정된 값을 유지하므로 currentUser는 로그용으로만 사용됩니다.</p>
     *
     * @param eqpId 설비 비즈니스 ID
     * @param params 수정할 파라미터 목록
     * @param currentUser 저장을 수행하는 사용자 ID (로그용)
     */
    @Override
    public void saveEditParams(final String eqpId, final List<EqpParamEdit> params, final String currentUser) {
        validateEqpId(eqpId);
        if (params == null || params.isEmpty()) {
            log.warn("EDIT 파라미터 저장 요청 - 파라미터 목록이 비어 있습니다. eqpId={}", eqpId);
            return;
        }

        log.info("EDIT 파라미터 저장 시작. eqpId={}, paramCount={}, currentUser={}", eqpId, params.size(), currentUser);

        final TcEqp eqp = resolveEqp(eqpId);
        final long eqpKey = eqp.eqpKey();

        // 현재 EDIT 버전의 created_by를 유지하기 위해 기존 EDIT 파라미터 조회
        final List<TcEqpParam> existingEditParams = eqpParamStore.findAllByEqpKeyAndVersion(eqpKey, EDIT_VERSION);
        final java.util.Map<String, String> createdByMap = new java.util.HashMap<>();
        for (TcEqpParam existing : existingEditParams) {
            if (existing.createdBy() != null) {
                createdByMap.put(existing.paramName(), existing.createdBy());
            }
        }

        for (EqpParamEdit edit : params) {
            if (edit.paramName() == null || edit.paramName().isBlank()) {
                continue;
            }
            // 기존 created_by 유지, 없으면 currentUser 사용
            final String createdBy = createdByMap.getOrDefault(edit.paramName(), currentUser);
            final UpsertTcEqpParam command = new UpsertTcEqpParam(
                    eqpKey,
                    edit.paramName(),
                    EDIT_VERSION,
                    edit.paramValue(),
                    edit.description(),
                    createdBy
            );
            eqpParamStore.upsert(command);
        }

        log.info("EDIT 파라미터 저장 완료. eqpId={}, paramCount={}", eqpId, params.size());
    }

    /**
     * 체크인을 수행합니다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>EDIT 파라미터 목록 조회</li>
     *   <li>각 EDIT 파라미터를 newVersion으로 INSERT (description = versionDescription)</li>
     *   <li>EDIT 버전 전체 DELETE</li>
     * </ol>
     *
     * @param eqpId 설비 비즈니스 ID
     * @param newVersion 저장할 신규 버전 이름
     * @param versionDescription 버전 설명 (null 허용)
     * @param currentUser 체크인을 수행하는 사용자 ID
     */
    @Override
    public void checkin(final String eqpId, final String newVersion, final String versionDescription, final String currentUser) {
        validateEqpId(eqpId);
        if (newVersion == null || newVersion.isBlank()) {
            throw new UiBadRequestException("newVersion은 비어 있을 수 없습니다.");
        }

        log.info("설비 파라미터 체크인 시작. eqpId={}, newVersion={}, currentUser={}", eqpId, newVersion, currentUser);

        final TcEqp eqp = resolveEqp(eqpId);
        final long eqpKey = eqp.eqpKey();

        final List<TcEqpParam> editParams = eqpParamStore.findAllByEqpKeyAndVersion(eqpKey, EDIT_VERSION);
        if (editParams.isEmpty()) {
            log.warn("체크인 대상 EDIT 파라미터가 없습니다. eqpId={}", eqpId);
        }

        // EDIT → newVersion 으로 INSERT
        for (TcEqpParam editParam : editParams) {
            final UpsertTcEqpParam command = new UpsertTcEqpParam(
                    eqpKey,
                    editParam.paramName(),
                    newVersion,
                    editParam.paramValue(),
                    versionDescription != null ? versionDescription : editParam.description(),
                    currentUser
            );
            eqpParamStore.upsert(command);
        }

        // EDIT 버전 전체 삭제 (잠금 해제)
        eqpParamStore.deleteAllByEqpKeyAndVersion(eqpKey, EDIT_VERSION);

        log.info("설비 파라미터 체크인 완료. eqpId={}, newVersion={}, paramCount={}", eqpId, newVersion, editParams.size());
    }

    /**
     * eqpId로 설비를 조회하고 없으면 400을 반환합니다.
     */
    private TcEqp resolveEqp(final String eqpId) {
        final Optional<TcEqp> optionalEqp = eqpStore.findByEqpId(eqpId);
        if (optionalEqp.isEmpty()) {
            log.warn("설비를 찾을 수 없습니다. eqpId={}", eqpId);
            throw new UiBadRequestException("설비를 찾을 수 없습니다. eqpId=" + eqpId);
        }
        return optionalEqp.get();
    }

    /**
     * checkout 경쟁 구간 직렬화를 위해 설비 행을 잠금 조회합니다.
     *
     * @param eqpId 설비 비즈니스 ID
     * @return 잠금 조회된 설비
     */
    private TcEqp resolveEqpForCheckout(final String eqpId) {
        final Optional<TcEqp> optionalEqp = eqpStore.findByEqpIdForUpdate(eqpId);
        if (optionalEqp.isEmpty()) {
            log.warn("체크아웃 대상 설비를 찾을 수 없습니다. eqpId={}", eqpId);
            throw new UiBadRequestException("설비를 찾을 수 없습니다. eqpId=" + eqpId);
        }
        return optionalEqp.get();
    }

    /**
     * EDIT 버전 파라미터에서 체크아웃 사용자를 추출합니다.
     *
     * <p>경합 상황에서 재조회가 실패하더라도 409 충돌 응답 흐름을 유지하기 위해
     * 사용자 식별값은 null을 반환하고, 최종 메시지 문구는 예외 계층에서 정규화합니다.</p>
     */
    private String resolveCheckedOutBy(final long eqpKey) {
        try {
            final List<TcEqpParam> editParams = eqpParamStore.findAllByEqpKeyAndVersion(eqpKey, EDIT_VERSION);
            return editParams.stream()
                    .map(TcEqpParam::createdBy)
                    .filter(by -> by != null && !by.isBlank())
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("체크아웃 사용자 재조회 실패. eqpKey={}", eqpKey, e);
            return null;
        }
    }

    /**
     * eqpId 입력값 유효성을 검증합니다.
     */
    private void validateEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new UiBadRequestException("eqpId는 비어 있을 수 없습니다.");
        }
    }
}
