package com.moyeobom.common.config;

import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각과 무작위 값은 주입받아 쓴다. 테스트에서 고정 Clock과 시드 있는 Random으로 바꿔 끼운다.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RandomGenerator randomGenerator() {
        // 스케줄러 스레드와 요청 스레드가 함께 쓰므로 스레드 안전한 Random을 쓴다
        return new Random();
    }
}
