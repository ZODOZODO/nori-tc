package com.nori.tc.apps.uibackend;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 앱 모듈 스모크 테스트입니다.
 *
 * <p>현재 앱 모듈은 조립(Composition Root) 역할만 담당합니다.</p>
 * <p>외부 인프라(DB/Kafka) 의존성이 필요한 통합 컨텍스트 테스트는 각 어댑터 모듈에서 별도로 수행합니다.</p>
 */
public class TcUiBackendApplicationTest {

    /**
     * 앱 진입점 클래스가 테스트 클래스패스에서 정상 로딩되는지 확인합니다.
     */
    @Test
    void applicationClassShouldBeLoadable() {
        Assertions.assertNotNull(TcUiBackendApplication.class, "Application class must be present");
    }
}
