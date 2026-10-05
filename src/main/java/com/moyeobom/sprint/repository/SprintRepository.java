package com.moyeobom.sprint.repository;

import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.sprint.domain.SprintStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    Optional<Sprint> findByGuestIdAndStatus(Long guestId, SprintStatus status);

    boolean existsByGuestIdAndStatus(Long guestId, SprintStatus status);

    /**
     * 같은 게스트의 집중 시작, 완료, 마무리 요청이 겹치지 않도록 열린 스프린트 행을 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sprint s where s.guestId = :guestId and s.status = com.moyeobom.sprint.domain.SprintStatus.OPEN")
    Optional<Sprint> findOpenForUpdate(Long guestId);

    Optional<Sprint> findFirstByGuestIdAndStatusOrderByClosedAtDesc(Long guestId, SprintStatus status);
}
