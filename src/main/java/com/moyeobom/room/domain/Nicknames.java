package com.moyeobom.room.domain;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 형용사 30개와 명사 30개를 조합한 랜덤 닉네임(900가지). 중복을 허용한다.
 */
public final class Nicknames {

    static final List<String> ADJECTIVES = List.of(
            "졸린", "조용한", "부지런한", "느긋한", "반짝이는", "꼼꼼한", "용감한", "수줍은", "씩씩한", "차분한",
            "다정한", "엉뚱한", "성실한", "포근한", "명랑한", "푸른", "따뜻한", "상냥한", "단단한", "말랑한",
            "날쌘", "신중한", "행복한", "동그란", "작은", "커다란", "배고픈", "열정적인", "침착한", "산뜻한");

    static final List<String> NOUNS = List.of(
            "수달", "연필", "고양이", "다람쥐", "부엉이", "펭귄", "지우개", "공책", "토끼", "거북이",
            "고래", "여우", "판다", "너구리", "햄스터", "코알라", "참새", "형광펜", "책갈피", "선인장",
            "감자", "도토리", "구름", "별", "달팽이", "강아지", "오리", "곰", "사슴", "해바라기");

    private Nicknames() {
    }

    public static String random(RandomGenerator random) {
        return ADJECTIVES.get(random.nextInt(ADJECTIVES.size())) + " " + NOUNS.get(random.nextInt(NOUNS.size()));
    }
}
