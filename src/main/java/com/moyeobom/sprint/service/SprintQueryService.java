package com.moyeobom.sprint.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.sprint.domain.SprintStatus;
import com.moyeobom.sprint.repository.SprintRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 기능(할 일, 집중, 열람실)이 열린 스프린트를 확인할 때 쓰는 조회 전용 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SprintQueryService {

    private final SprintRepository sprintRepository;

    public boolean hasOpenSprint(Long guestId) {
        return sprintRepository.existsByGuestIdAndStatus(guestId, SprintStatus.OPEN);
    }

    public Optional<Sprint> findOpenSprint(Long guestId) {
        return sprintRepository.findByGuestIdAndStatus(guestId, SprintStatus.OPEN);
    }

    public Sprint getOpenSprint(Long guestId) {
        return findOpenSprint(guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPRINT_NOT_FOUND));
    }

    /**
     * 호출한 쪽의 트랜잭션 안에서 열린 스프린트 행을 잠근다. 게스트 단위로 상태 변경을 한 줄로 세운다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Sprint lockOpenSprint(Long guestId) {
        return sprintRepository.findOpenForUpdate(guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPRINT_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Sprint> findOpenSprintForUpdate(Long guestId) {
        return sprintRepository.findOpenForUpdate(guestId);
    }

    public Optional<Sprint> findLastClosedSprint(Long guestId) {
        return sprintRepository.findFirstByGuestIdAndStatusOrderByClosedAtDesc(guestId, SprintStatus.CLOSED);
    }
}
