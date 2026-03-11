package com.nori.tc.ui.adapters.web.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * branch model 생성 요청 DTO입니다.
 *
 * @param suffix branch 이름에 포함할 사용자 입력 suffix
 */
public record ModelBranchCreateRequest(

        @NotBlank(message = "suffix는 필수입니다.")
        String suffix
) {
}
