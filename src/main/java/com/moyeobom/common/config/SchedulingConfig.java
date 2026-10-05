package com.moyeobom.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 가상 메이트 전이, 열람실 유예, 휴식 알림, 세션 정리를 모두 이 스케줄러 하나로 돌린다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    public static final String TASK_SCHEDULER = "taskScheduler";

    @Bean(name = TASK_SCHEDULER)
    public ThreadPoolTaskScheduler taskScheduler(Clock clock) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("moyeobom-scheduler-");
        scheduler.setClock(clock);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
