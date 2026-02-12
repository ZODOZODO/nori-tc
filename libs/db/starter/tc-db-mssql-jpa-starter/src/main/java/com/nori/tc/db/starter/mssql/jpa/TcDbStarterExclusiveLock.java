package com.nori.tc.db.starter.mssql.jpa;

/**
 * Starter 배타 락(Exclusive Lock) (FIX)
 *
 * 목적
 * - "starter를 정확히 1개만" 선택하도록 강제하기 위한 장치입니다.
 * - tc-db-*-*-starter 계열 모듈은 모두 동일한 Bean 이름을 등록하도록 설계합니다.
 * - 따라서 2개 이상 starter가 클래스패스에 포함되면,
 *   동일 Bean 이름 충돌로 인해 Spring Context가 즉시 실패합니다(fail-fast).
 *
 * 주의
 * - 이 방식은 의도적으로 "조립 실수"를 빠르게 드러내는 목적입니다.
 */
public final class TcDbStarterExclusiveLock {

    private final String starterId;

    
    /**
     * DB 스타터 구성 구성 요소를 초기화합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param starterId DB 스타터 구성 처리에 사용하는 입력 값
     */
    public TcDbStarterExclusiveLock(String starterId) {
        this.starterId = starterId;
    }

    
    /**
     * DB 스타터 구성의 현재 값을 조회합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @return DB 스타터 구성 처리 결과
     */
    public String getStarterId() {
        return starterId;
    }
}
