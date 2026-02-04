package com.nori.tc.db.core.common;

/**
 * 단순 페이징 요청 객체.
 *
 * 설계 의도
 * - JPA/MyBatis 같은 구현 기술과 무관하게 공통으로 쓸 수 있는 최소 사양만 둡니다.
 * - offset/limit 기반은 대부분의 DB에서 쉽게 구현 가능합니다.
 *
 * 용어 주의
 * - 여기서 offset은 "몇 개를 건너뛸지"
 * - limit은 "최대 몇 개를 가져올지" 입니다.
 */
public record PageRequest(int offset, int limit) {

    /**
     * ✅ 권장: 명시적 팩토리
     * - 코드에서 new PageRequest(...)를 직접 쓰는 대신,
     *   PageRequest.of(...) 형태로 통일하면 가독성이 좋아집니다.
     *
     * @param offset 0 이상
     * @param limit  1 이상
     */
    public static PageRequest of(int offset, int limit) {
        return new PageRequest(offset, limit);
    }

    /**
     * 기본값: offset=0, limit=100
     * (운영에서는 상위 계층에서 limit 상한을 정책으로 강제하는 것을 권장)
     */
    public static PageRequest defaultPage() {
        return new PageRequest(0, 100);
    }

    /**
     * record compact constructor
     * - 생성 시점에 값 검증을 강제합니다.
     */
    public PageRequest {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        // 과도한 limit은 상위 계층(서비스/유스케이스)에서 정책으로 제한하는 편이 좋습니다.
    }
}
