package com.moyeobom.common.message;

import java.util.List;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 할 일 완료, 휴식 알림, 마무리 때 하나를 무작위로 보여주는 리프레시 문구.
 */
@Component
@RequiredArgsConstructor
public class RefreshMessages {

    static final List<String> MESSAGES = List.of(
            "스트레칭 한번 해볼까요?",
            "물 한 잔 마시고 올까요?",
            "창밖을 보며 눈을 잠깐 쉬어요",
            "어깨를 크게 몇 번 돌려볼까요?",
            "숨을 깊게 세 번 쉬어볼까요?",
            "자리에서 일어나 잠깐 걸어볼까요?"
    );

    private final RandomGenerator random;

    public String pick() {
        return MESSAGES.get(random.nextInt(MESSAGES.size()));
    }
}
