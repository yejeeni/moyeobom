package com.moyeobom.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    GUEST_NOT_FOUND(HttpStatus.UNAUTHORIZED, "게스트 정보를 찾을 수 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    SPRINT_ALREADY_OPEN(HttpStatus.CONFLICT, "이미 진행 중인 스프린트가 있습니다."),
    SPRINT_NOT_FOUND(HttpStatus.NOT_FOUND, "진행 중인 스프린트가 없습니다."),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "할 일을 찾을 수 없습니다."),
    TASK_ALREADY_DONE(HttpStatus.CONFLICT, "이미 완료한 할 일입니다."),
    TASK_HAS_RECORDS(HttpStatus.CONFLICT, "집중 기록이 있는 할 일은 삭제할 수 없습니다."),
    TASK_ALREADY_CARRIED(HttpStatus.CONFLICT, "이미 이월한 할 일입니다."),
    NO_TASK_FOR_ROOM(HttpStatus.CONFLICT, "할 일이 있어야 열람실에 입장할 수 있습니다."),
    FOCUS_NOT_RUNNING(HttpStatus.NOT_FOUND, "진행 중인 집중 세션이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
