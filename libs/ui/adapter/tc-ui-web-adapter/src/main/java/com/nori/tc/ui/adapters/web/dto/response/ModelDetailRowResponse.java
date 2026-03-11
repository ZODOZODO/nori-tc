package com.nori.tc.ui.adapters.web.dto.response;

import java.util.List;

/**
 * Model 상세 노드의 공통 테이블 row 응답 DTO입니다.
 *
 * @param values 컬럼 순서에 맞는 셀 값 목록
 */
public record ModelDetailRowResponse(
        List<String> values
) {
}
