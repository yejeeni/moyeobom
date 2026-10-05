package com.moyeobom.focus.repository;

import com.moyeobom.focus.domain.FocusSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    boolean existsByTaskId(Long taskId);
}
