package com.soccergame.simulator;

import com.soccergame.domain.config.ConfigLoader;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorTest {
    private final GameConfig config = ConfigLoader.load(Path.of(System.getProperty("game.config.dir")));
    private final YearRunner runner = new YearRunner(new GameEngine(config));

    @Test
    void everyStrategyFinishesTheYearDeterministically() {
        for (Strategy strategy : Strategies.all()) {
            GameState a = runner.run(42, strategy);
            GameState b = runner.run(42, strategy);
            assertThat(a.finished).isTrue();
            assertThat(a.stats.asMap()).isEqualTo(b.stats.asMap());
            assertThat(a.matches).isEqualTo(b.matches);
            assertThat(a.eventHistory).isEqualTo(b.eventHistory);
        }
    }

    @Test
    void reportRendersAllSections() {
        String report = SimulatorMain.run(config, 5, 1);
        assertThat(report).contains("무작위", "훈련 위주", "균형", "## 1.", "## 5.", "리그 8위");
    }
}
