package com.nori.tc.ui.core.port.db;

import com.nori.tc.db.domain.model.TcModel;

import java.util.List;

/**
 * branch model 생성/정리 기능을 담당하는 관리 포트입니다.
 *
 * <p>대상 엔드포인트:</p>
 * <ul>
 *   <li>{@code POST /api/model/{modelKey}/branches}</li>
 *   <li>{@code DELETE /api/model/{modelKey}/branches/deprecated}</li>
 * </ul>
 */
public interface ModelBranchCommandPort {

    /**
     * branch model 생성 명령입니다.
     *
     * @param parentModelKey 부모 root model의 model_key
     * @param suffix branch 이름에 사용할 사용자 입력 suffix
     * @param sourceModelVersionKey 복제 기준 root model_version_key (null이면 최신 버전)
     * @param currentUser 현재 로그인 사용자 ID
     */
    record CreateBranchModelCommand(
            long parentModelKey,
            String suffix,
            Long sourceModelVersionKey,
            String currentUser
    ) {
    }

    /**
     * deprecated branch 정리 결과입니다.
     *
     * @param deletedCount 삭제된 branch 개수
     * @param deletedModelKeys 삭제된 branch model_key 목록
     * @param deletedModelNames 삭제된 branch model_name 목록
     */
    record DeleteDeprecatedBranchesResult(
            int deletedCount,
            List<Long> deletedModelKeys,
            List<String> deletedModelNames
    ) {
    }

    /**
     * branch model을 생성합니다.
     *
     * @param command 생성 명령
     * @return 생성된 branch의 최신 모델 버전 스냅샷
     */
    TcModel createBranchModel(CreateBranchModelCommand command);

    /**
     * 선택된 root model에 연결된 deprecated branch를 일괄 삭제합니다.
     *
     * @param parentModelKey 부모 root model의 model_key
     * @return 삭제 결과
     */
    DeleteDeprecatedBranchesResult deleteDeprecatedBranches(long parentModelKey);
}
