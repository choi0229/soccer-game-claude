package com.soccergame.domain.config;

import com.soccergame.domain.TestConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void loadsAllConfigFiles() {
        GameConfig config = TestConfig.load();
        assertThat(config.trainedStatKeys()).hasSize(12);
        assertThat(config.passiveStatKeys()).hasSize(6);
        assertThat(config.trainingMenus().menus()).hasSize(7);
        assertThat(config.scenes().scenes()).hasSize(9);
        assertThat(config.schools().types().stream().mapToInt(Schools.SchoolTypeDef::count).sum()).isEqualTo(32);
        assertThat(config.rules().archetype().primaryStats()).containsExactly("shotPower", "heading", "kickPower");
    }

    @Test
    void allMenusStartUnlocked() {
        assertThat(TestConfig.load().trainingMenus().menus()).allMatch(TrainingMenus.TrainingMenu::unlocked);
    }

    @Test
    void gradeBoundaries() {
        GameConfig config = TestConfig.load();
        assertThat(config.grade(0)).isEqualTo("G");
        assertThat(config.grade(29.99)).isEqualTo("G");
        assertThat(config.grade(30)).isEqualTo("F");
        assertThat(config.grade(49.9)).isEqualTo("E");
        assertThat(config.grade(59)).isEqualTo("D");
        assertThat(config.grade(60)).isEqualTo("C");
        assertThat(config.grade(79.9)).isEqualTo("B");
        assertThat(config.grade(80)).isEqualTo("A");
        assertThat(config.grade(90)).isEqualTo("S");
        assertThat(config.grade(100)).isEqualTo("S");
    }
}
