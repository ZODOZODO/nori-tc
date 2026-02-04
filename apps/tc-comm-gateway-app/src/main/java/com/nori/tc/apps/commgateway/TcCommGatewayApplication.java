package com.nori.tc.apps.commgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * tc-comm-gateway-app 실행 엔트리 포인트
 *
 * - Spring Boot 플러그인의 bootJar 생성 시 메인 클래스를 찾기 위해 필요
 * - DB 설정은 application.yaml이 ./config/tc-db.properties 를 import 하므로,
 *   런타임에서는 외부 파일만 수정하면 된다.
 */
@SpringBootApplication
public class TcCommGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(TcCommGatewayApplication.class, args);
    }
}
