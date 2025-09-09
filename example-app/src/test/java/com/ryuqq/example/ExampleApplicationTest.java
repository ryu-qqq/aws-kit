package com.ryuqq.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 예제 애플리케이션 기본 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class ExampleApplicationTest {

    @Test
    void contextLoads() {
        // Spring Boot 애플리케이션 컨텍스트 로딩 테스트
    }
}