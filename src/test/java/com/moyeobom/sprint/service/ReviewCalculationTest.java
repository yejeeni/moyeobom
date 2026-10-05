package com.moyeobom.sprint.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.sprint.dto.ReviewDtos.ReviewResponse;
import com.moyeobom.sprint.dto.ReviewDtos.ReviewTask;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.dto.TaskDtos.TaskTimes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewCalculationTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 9, 0);

    @Test
    void 예상이_있는_완료_할_일만_예상_대비_실제_합계에_들어간다() {
        Sprint sprint = withId(Sprint.open(1L, NOW), 12L);
        Task doneWithEstimate = withId(Task.create(12L, "알고리즘 3문제", 90, 1, NOW), 51L);
        doneWithEstimate.complete(NOW);
        Task doneWithoutEstimate = withId(Task.create(12L, "메일 답장", null, 2, NOW), 52L);
        doneWithoutEstimate.complete(NOW);
        Task todo = withId(Task.create(12L, "자소서 수정", 30, 3, NOW), 53L);

        ReviewResponse review = ReviewService.review(sprint, List.of(doneWithEstimate, doneWithoutEstimate, todo),
                Map.of(51L, new TaskTimes(6600, 6600, 2),
                        52L, new TaskTimes(900, 900, 1),
                        53L, new TaskTimes(600, 4200, 1)));

        assertThat(review.totalFocusSeconds()).isEqualTo(6600 + 900 + 600);
        assertThat(review.completedCount()).isEqualTo(2);
        assertThat(review.estimatedSecondsOfDone()).isEqualTo(5400);
        assertThat(review.actualSecondsOfDone()).isEqualTo(6600);

        ReviewTask first = review.tasks().getFirst();
        assertThat(first.estimatedSeconds()).isEqualTo(5400);
        assertThat(first.diffSeconds()).isEqualTo(1200);

        ReviewTask noEstimate = review.tasks().get(1);
        assertThat(noEstimate.estimatedSeconds()).isNull();
        assertThat(noEstimate.diffSeconds()).isNull();

        ReviewTask carried = review.tasks().get(2);
        assertThat(carried.diffSeconds()).isEqualTo(600 - 1800);
        assertThat(carried.cumulativeSeconds()).isEqualTo(4200);
    }

    @Test
    void 기록이_없는_할_일은_0초다() {
        Sprint sprint = withId(Sprint.open(1L, NOW), 12L);
        Task task = withId(Task.create(12L, "영어 단어", 20, 1, NOW), 60L);

        ReviewResponse review = ReviewService.review(sprint, List.of(task), Map.of());

        assertThat(review.totalFocusSeconds()).isZero();
        assertThat(review.tasks().getFirst().actualSeconds()).isZero();
        assertThat(review.tasks().getFirst().diffSeconds()).isEqualTo(-1200);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
