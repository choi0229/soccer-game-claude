package com.soccergame.domain.config;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 설정 디렉터리에서 YAML 여섯 개를 읽어 GameConfig 를 만든다. */
public final class ConfigLoader {
    public static final String GAME = "game.yaml";
    public static final String TRAINING_MENUS = "training-menus.yaml";
    public static final String SCENES = "scenes.yaml";
    public static final String SCHOOLS = "schools.yaml";
    public static final String EVENTS = "events.yaml";
    public static final String CLUTCH_MOMENTS = "clutch-moments.yaml";

    private static final ObjectMapper MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ConfigLoader() {
    }

    public static GameConfig load(Path dir) {
        return new GameConfig(
                read(dir.resolve(GAME), Rules.class),
                read(dir.resolve(TRAINING_MENUS), TrainingMenus.class),
                read(dir.resolve(SCENES), Scenes.class),
                read(dir.resolve(SCHOOLS), Schools.class),
                read(dir.resolve(EVENTS), Events.class),
                read(dir.resolve(CLUTCH_MOMENTS), ClutchMoments.class));
    }

    private static <T> T read(Path file, Class<T> type) {
        try (InputStream in = Files.newInputStream(file)) {
            return MAPPER.readValue(in, type);
        } catch (IOException | JacksonException e) {
            throw new ConfigException("설정 파일을 읽지 못했습니다: " + file + " - " + e.getMessage(), e);
        }
    }
}
