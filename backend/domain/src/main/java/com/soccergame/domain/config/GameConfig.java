package com.soccergame.domain.config;

import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.config.Rules.StatDef;
import com.soccergame.domain.config.Scenes.SceneDef;
import com.soccergame.domain.config.Schools.DefenderTypeDef;
import com.soccergame.domain.config.Schools.SchoolTypeDef;
import com.soccergame.domain.config.TrainingMenus.TrainingMenu;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.TrainingSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 설정 파일 전체와 조회용 색인. 만들 때 교차 검증을 한다. */
public final class GameConfig {
    /** 엔진이 이름으로 참조하는 패시브 키 */
    public static final String FITNESS = "fitness";
    public static final String MOMENTUM = "momentum";
    public static final String CLUTCH = "clutch";
    public static final String MENTAL = "mental";

    private final Rules rules;
    private final TrainingMenus trainingMenus;
    private final Scenes scenes;
    private final Schools schools;
    private final Events events;

    private final Map<String, StatDef> statsByKey = new LinkedHashMap<>();
    private final List<String> trainedKeys = new ArrayList<>();
    private final List<String> passiveKeys = new ArrayList<>();
    private final Map<String, TrainingMenu> menusById = new LinkedHashMap<>();
    private final Map<String, SceneDef> scenesById = new LinkedHashMap<>();
    private final Map<String, EventDef> eventsById = new LinkedHashMap<>();
    private final Map<String, SchoolTypeDef> schoolTypesByKey = new LinkedHashMap<>();
    private final Map<String, DefenderTypeDef> defenderTypesByKey = new LinkedHashMap<>();

    public GameConfig(Rules rules, TrainingMenus trainingMenus, Scenes scenes, Schools schools, Events events) {
        this.rules = rules;
        this.trainingMenus = trainingMenus;
        this.scenes = scenes;
        this.schools = schools;
        this.events = events;
        rules.stats().trained().forEach(s -> {
            put(statsByKey, s.key(), s, "능력치");
            trainedKeys.add(s.key());
        });
        rules.stats().passive().forEach(s -> {
            put(statsByKey, s.key(), s, "능력치");
            passiveKeys.add(s.key());
        });
        trainingMenus.menus().forEach(m -> put(menusById, m.id(), m, "훈련 메뉴"));
        scenes.scenes().forEach(s -> put(scenesById, s.id(), s, "장면"));
        events.events().forEach(e -> put(eventsById, e.id(), e, "이벤트"));
        schools.types().forEach(t -> put(schoolTypesByKey, t.key(), t, "학교 유형"));
        schools.defenderTypes().forEach(d -> put(defenderTypesByKey, d.key(), d, "수비수 유형"));
        validate();
    }

    private static <T> void put(Map<String, T> map, String key, T value, String what) {
        if (map.putIfAbsent(key, value) != null) {
            throw new ConfigException(what + " id 중복: " + key);
        }
    }

    private void validate() {
        for (String key : List.of(FITNESS, MOMENTUM, CLUTCH, MENTAL)) {
            requireStat(key, "엔진 필수 패시브");
        }
        rules.archetype().primaryStats().forEach(k -> requireStat(k, "유형 주력 능력치"));
        for (TrainingMenu menu : trainingMenus.menus()) {
            if (menu.stats() == null || menu.stats().isEmpty()) {
                throw new ConfigException("훈련 메뉴에 능력치가 없습니다: " + menu.id());
            }
            menu.stats().forEach(k -> requireStat(k, "훈련 메뉴 " + menu.id()));
        }
        for (TrainingSlot slot : TrainingSlot.values()) {
            String id = trainingMenus.defaults().get(slot);
            if (id == null || !menusById.containsKey(id)) {
                throw new ConfigException("칸 " + slot + " 의 기본 메뉴가 없습니다: " + id);
            }
        }
        for (SceneDef scene : scenes.scenes()) {
            scene.stats().forEach(k -> requireStat(k, "장면 " + scene.id()));
            if (scene.outcome() == Scenes.Outcome.EXTRA_SCENE && !scenesById.containsKey(scene.extraScene())) {
                throw new ConfigException("장면 " + scene.id() + " 의 추가 장면이 없습니다: " + scene.extraScene());
            }
        }
        for (DefenderTypeDef d : schools.defenderTypes()) {
            requireStat(d.passive(), "수비수 유형 " + d.key());
        }
        int regions = schools.regions().size();
        for (SchoolTypeDef t : schools.types()) {
            if (t.count() % regions != 0) {
                throw new ConfigException("학교 유형 " + t.key() + " 의 수가 권역 수로 나누어지지 않습니다");
            }
        }
        if (!schoolTypesByKey.containsKey(schools.player().schoolType())) {
            throw new ConfigException("플레이어 학교 유형이 없습니다: " + schools.player().schoolType());
        }
        int needNames = schools.types().stream().mapToInt(SchoolTypeDef::count).sum();
        if (schools.nameParts().first().size() * schools.nameParts().second().size() < needNames) {
            throw new ConfigException("학교 이름 조각이 부족합니다");
        }
        for (EventDef e : events.events()) {
            if (e.choices() == null || e.choices().size() < 2 || e.choices().size() > 3) {
                throw new ConfigException("이벤트 선택지는 2~3개여야 합니다: " + e.id());
            }
            for (Events.Choice c : e.choices()) {
                if (c.effects() != null && c.effects().stat() != null) {
                    c.effects().stat().keySet().forEach(k -> requireStat(k, "이벤트 " + e.id()));
                }
            }
        }
    }

    private void requireStat(String key, String where) {
        if (!statsByKey.containsKey(key)) {
            throw new ConfigException(where + ": 알 수 없는 능력치 " + key);
        }
    }

    public Rules rules() {
        return rules;
    }

    public TrainingMenus trainingMenus() {
        return trainingMenus;
    }

    public Scenes scenes() {
        return scenes;
    }

    public Schools schools() {
        return schools;
    }

    public Events events() {
        return events;
    }

    public List<String> trainedStatKeys() {
        return Collections.unmodifiableList(trainedKeys);
    }

    public List<String> passiveStatKeys() {
        return Collections.unmodifiableList(passiveKeys);
    }

    public List<String> allStatKeys() {
        List<String> all = new ArrayList<>(trainedKeys);
        all.addAll(passiveKeys);
        return all;
    }

    public StatDef stat(String key) {
        return statsByKey.get(key);
    }

    public boolean isStat(String key) {
        return statsByKey.containsKey(key);
    }

    public boolean isPrimary(String statKey) {
        return rules.archetype().primaryStats().contains(statKey);
    }

    public Optional<TrainingMenu> menu(String id) {
        return Optional.ofNullable(menusById.get(id));
    }

    public SceneDef scene(String id) {
        return scenesById.get(id);
    }

    public Optional<EventDef> event(String id) {
        return Optional.ofNullable(eventsById.get(id));
    }

    public SchoolTypeDef schoolType(String key) {
        return schoolTypesByKey.get(key);
    }

    public DefenderTypeDef defenderType(String key) {
        return defenderTypesByKey.get(key);
    }

    public String grade(double value) {
        for (Rules.GradeDef g : rules.stats().grades()) {
            if (value >= g.min()) {
                return g.grade();
            }
        }
        return rules.stats().grades().getLast().grade();
    }

    public double initialAffinity(Axis axis) {
        return rules.relationships().start().of(axis);
    }
}
