package com.moyeobom.focus.service;

import com.moyeobom.focus.repository.FocusSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FocusQueryService {

    private final FocusSessionRepository focusSessionRepository;

    /** 진행 중인 세션을 포함해 집중 기록이 하나라도 있는지 */
    public boolean hasRecords(Long taskId) {
        return focusSessionRepository.existsByTaskId(taskId);
    }
}
