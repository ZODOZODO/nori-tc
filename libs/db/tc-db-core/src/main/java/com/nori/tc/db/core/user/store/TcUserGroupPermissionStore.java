package com.nori.tc.db.core.user.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroupPermission;
import com.nori.tc.db.domain.user.TcUserGroupPermission;

/**
 * tc_user_group_permission CRUD 인터페이스.
 *
 * <p>
 * 설계 기준:
 * <ul>
 * <li>Unique Key는 (group_id, perm_id)이며, 이를 기준으로 upsert를 수행합니다.</li>
 * <li>list 조회는 group_id 기준으로 페이징을 지원합니다.</li>
 * <li>삭제는 (group_id, perm_id) 단위로 수행합니다.</li>
 * </ul>
 * </p>
 */
public interface TcUserGroupPermissionStore {

    /**
     * (group_id, perm_id) 유니크 키 기준으로 갱신/생성을 수행합니다.
     */
    TcUserGroupPermission upsert(UpsertTcUserGroupPermission command);

    /**
     * (group_id, perm_id) 단건 조회
     */
    Optional<TcUserGroupPermission> findByGroupIdPermId(long groupId, long permId);

    /**
     * group_id 기준 목록 조회 (offset/limit 페이징 적용)
     */
    List<TcUserGroupPermission> findAllByGroupId(long groupId, PageRequest page);

    /**
     * (group_id, perm_id) 단건 삭제
     */
    void deleteByGroupIdPermId(long groupId, long permId);
}
