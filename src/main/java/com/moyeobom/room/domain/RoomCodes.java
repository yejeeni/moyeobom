package com.moyeobom.room.domain;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * 열람실 입장 코드. 헷갈리기 쉬운 글자(0·O, 1·I·L)를 뺀 영문 대문자와 숫자 6자리.
 */
public final class RoomCodes {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;

    private RoomCodes() {
    }

    public static String random(RandomGenerator random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** 사용자가 입력한 코드를 비교할 수 있게 다듬는다: 공백·하이픈을 지우고 대문자로 */
    public static String normalize(String input) {
        return input == null ? "" : input.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }
}
