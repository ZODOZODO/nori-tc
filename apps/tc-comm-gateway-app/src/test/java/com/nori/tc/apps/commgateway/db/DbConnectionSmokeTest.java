package com.nori.tc.apps.commgateway.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 연결 스모크 테스트
 *
 * 목표
 * - 선택된 starter(JPA/MyBatis)와 무관하게 "연결" 자체가 정상인지 빠르게 확인합니다.
 *
 * 중요한 포인트
 * - spring.config.import 경로는 실행 워킹 디렉터리에 영향을 받습니다.
 *   그래서 테스트에서는 여러 상대 경로를 동시에 시도하도록 override 합니다.
 *
 * 주의
 * - 테스트 클래스 생성자에 DataSource를 받도록 만들면,
 *   JUnit이 먼저 인스턴스화하면서 ParameterResolver를 찾다가 실패합니다.
 *   따라서 @Autowired 필드 주입(또는 메서드 파라미터 주입)을 사용합니다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // repo root에서 실행하면 첫 번째가 먹고,
        // 모듈 디렉터리에서 실행하면 뒤쪽 후보가 먹을 수 있게 다 열어둡니다.
        "spring.config.import=optional:file:config/tc-db.properties,optional:file:../config/tc-db.properties,optional:file:../../config/tc-db.properties",
        // 이 앱은 웹앱이 아니므로 테스트에서도 non-web로 고정
        "spring.main.web-application-type=none"
})
class DbConnectionSmokeTest {

    /**
     * Spring 컨텍스트에서 제공하는 DataSource를 주입받습니다.
     * (starter가 제대로 DataSource를 구성했다면 여기로 들어옵니다.)
     */
    @Autowired
    private DataSource dataSource;

    @Test
    void canConnect() throws Exception {
        // DataSource 주입 확인(실패 시 바로 원인 확인 가능)
        assertThat(dataSource).isNotNull();

        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(2)).isTrue();

            DatabaseMetaData meta = conn.getMetaData();
            // 콘솔 확인용(테스트 성공 시 어떤 DB로 붙었는지 확인)
            System.out.println("[DB] product=" + meta.getDatabaseProductName());
            System.out.println("[DB] version=" + meta.getDatabaseProductVersion());
            System.out.println("[DB] url=" + meta.getURL());
            System.out.println("[DB] user=" + meta.getUserName());
        }
    }
}
