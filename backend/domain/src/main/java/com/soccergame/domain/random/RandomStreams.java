package com.soccergame.domain.random;

/**
 * 시드 하나에서 만든 독립 난수 흐름 두 개.
 * game: 능력치 시작값, 대진, 경기, 부상, 이벤트 발생 등 게임 판정 전용.
 * choice: 시뮬레이터 전략의 선택 전용. 게임 판정은 이 흐름을 쓰지 않는다.
 */
public record RandomStreams(Rng game, Rng choice) {
    private static final long GAME_SALT = 0x6A09E667F3BCC909L;
    private static final long CHOICE_SALT = 0xBB67AE8584CAA73BL;

    public static RandomStreams forSeed(long seed) {
        return new RandomStreams(gameStream(seed), choiceStream(seed));
    }

    public static Rng gameStream(long seed) {
        return new Rng(new Rng(seed ^ GAME_SALT).nextLong());
    }

    public static Rng choiceStream(long seed) {
        return new Rng(new Rng(seed ^ CHOICE_SALT).nextLong());
    }
}
