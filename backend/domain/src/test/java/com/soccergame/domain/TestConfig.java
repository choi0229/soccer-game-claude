package com.soccergame.domain;

import com.soccergame.domain.config.ConfigLoader;
import com.soccergame.domain.config.GameConfig;

import java.nio.file.Path;

/** 테스트는 저장소의 실제 config/ 를 읽는다. */
public final class TestConfig {
    private static GameConfig cached;

    private TestConfig() {
    }

    public static Path dir() {
        return Path.of(System.getProperty("game.config.dir", "../../config"));
    }

    public static synchronized GameConfig load() {
        if (cached == null) {
            cached = ConfigLoader.load(dir());
        }
        return cached;
    }
}
