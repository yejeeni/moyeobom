package com.moyeobom.focus.domain;

import com.moyeobom.room.domain.SeatState;
import java.time.Instant;

/**
 * 실제 유저의 현재 상태와 그 상태가 된 시각.
 */
public record MyState(SeatState state, Instant since) {
}
