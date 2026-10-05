package com.moyeobom.sprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeobom.common.time.Times;
import com.moyeobom.guest.domain.Guest;
import com.moyeobom.guest.repository.GuestRepository;
import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.sprint.repository.SprintRepository;
import com.moyeobom.support.IntegrationTestSupport;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.repository.TaskRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 애플리케이션 검사를 건너뛰어도 DB 제약이 직접 막는지 확인한다.
 */
class DatabaseConstraintTest extends IntegrationTestSupport {

    @Autowired
    GuestRepository guestRepository;

    @Autowired
    SprintRepository sprintRepository;

    @Autowired
    TaskRepository taskRepository;

    @Test
    void 게스트당_열린_스프린트는_하나뿐이다() {
        LocalDateTime now = Times.now(clock);
        Guest guest = guestRepository.save(Guest.issue(now));
        sprintRepository.saveAndFlush(Sprint.open(guest.getId(), now));

        assertThatThrownBy(() -> sprintRepository.saveAndFlush(Sprint.open(guest.getId(), now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 닫힌_스프린트는_여러_개일_수_있다() {
        LocalDateTime now = Times.now(clock);
        Guest guest = guestRepository.save(Guest.issue(now));
        Sprint first = sprintRepository.saveAndFlush(Sprint.open(guest.getId(), now));
        first.close(now);
        sprintRepository.saveAndFlush(first);
        Sprint second = sprintRepository.saveAndFlush(Sprint.open(guest.getId(), now));
        second.close(now);
        sprintRepository.saveAndFlush(second);

        assertThat(sprintRepository.saveAndFlush(Sprint.open(guest.getId(), now)).getId()).isNotNull();
    }

    @Test
    void 같은_할_일은_한_번만_이월된다() {
        LocalDateTime now = Times.now(clock);
        Guest guest = guestRepository.save(Guest.issue(now));
        Sprint closed = sprintRepository.saveAndFlush(Sprint.open(guest.getId(), now));
        Task original = taskRepository.saveAndFlush(Task.create(closed.getId(), "자소서", 60, 1, now));
        original.carry();
        taskRepository.saveAndFlush(original);
        closed.close(now);
        sprintRepository.saveAndFlush(closed);

        Sprint next = sprintRepository.saveAndFlush(Sprint.open(guest.getId(), now));
        taskRepository.saveAndFlush(Task.carryOver(original, next.getId(), 30, 1, now));

        assertThatThrownBy(() -> taskRepository.saveAndFlush(Task.carryOver(original, next.getId(), 30, 2, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
