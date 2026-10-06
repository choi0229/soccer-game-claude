package com.soccergame.api;

import com.soccergame.domain.config.ConfigLoader;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.GameEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class GameBeans {

    @Bean
    public GameConfig gameConfig(@Value("${game.config-dir}") String configDir) {
        return ConfigLoader.load(Path.of(configDir));
    }

    @Bean
    public GameEngine gameEngine(GameConfig config) {
        return new GameEngine(config);
    }
}
