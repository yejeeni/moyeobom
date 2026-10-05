package com.moyeobom.room.domain;

import java.util.random.RandomGenerator;

/**
 * 캐릭터는 파츠 번호만 보내고 그림은 클라이언트가 그린다.
 */
public record CharacterParts(int hair, int hairColor, int shirt, int skin) {

    static final int HAIR_COUNT = 8;
    static final int HAIR_COLOR_COUNT = 6;
    static final int SHIRT_COUNT = 8;
    static final int SKIN_COUNT = 5;

    public static CharacterParts random(RandomGenerator random) {
        return new CharacterParts(random.nextInt(HAIR_COUNT), random.nextInt(HAIR_COLOR_COUNT),
                random.nextInt(SHIRT_COUNT), random.nextInt(SKIN_COUNT));
    }
}
