package com.nori.tc.apps.businesscore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * tc-business-core-app의 진입점 클래스입니다.
 *
 * <p>주요 역할은 다음과 같습니다.</p>
 * <p>1) Spring Boot 애플리케이션 컨텍스트를 생성하고 초기화합니다.</p>
 * <p>2) 기동/종료 상태를 로그로 남겨 운영자가 실행 상태를 빠르게 확인할 수 있게 합니다.</p>
 */
@SpringBootApplication
public class TcBusinessCoreApplication {

    /**
     * 애플리케이션 기동/종료 로그 기록에 사용하는 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(TcBusinessCoreApplication.class);

    /**
     * tc-business-core-app을 기동합니다.
     *
     * @param args 런타임 전달 인자 목록
     */
    public static void main(String[] args) {
        final int argsCount = args == null ? 0 : args.length;
        log.info("tc-business-core-app 시작. argsCount={}", argsCount);

        final ConfigurableApplicationContext context = SpringApplication.run(TcBusinessCoreApplication.class, args);

        log.info("tc-business-core-app 시작 완료. beanDefinitionCount={}", context.getBeanDefinitionCount());
    }
}
