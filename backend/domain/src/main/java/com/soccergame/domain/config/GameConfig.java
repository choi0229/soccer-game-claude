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
    private final ClutchMoments clutchMoments;

    private final Map<String, StatDef> statsByKey = new LinkedHashMap<>();
    private final List<String> trainedKeys = new ArrayList<>();
    private final List<String> passiveKeys = new ArrayList<>();
    private final Map<String, TrainingMenu> menusById = new LinkedHashMap<>();
    private final Map<String, SceneDef> scenesById = new LinkedHashMap<>();
    private final Map<String, EventDef> eventsById = new LinkedHashMap<>();
    private final Map<String, SchoolTypeDef> schoolTypesByKey = new LinkedHashMap<>();
    private final Map<String, DefenderTypeDef> defenderTypesByKey = new LinkedHashMap<>();
    private final Map<String, Rules.TraitDef> traitsByKey = new LinkedHashMap<>();

    public GameConfig(Rules rules, TrainingMenus trainingMenus, Scenes scenes, Schools schools, Events events,
                      ClutchMoments clutchMoments) {
        this.rules = rules;
        this.trainingMenus = trainingMenus;
        this.scenes = scenes;
        this.schools = schools;
        this.events = events;
        this.clutchMoments = clutchMoments;
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
        rules.traits().list().forEach(t -> put(traitsByKey, t.key(), t, "특성"));
        validate();
    }

    private static <T> void put(Map<String, T> map, String key, T value, String what) {
        if (map.putIfAbsent(key, value) != null) {
            throw new ConfigException(what + " id 중복: " + key);
        }
    }

    /** 플레이어 학교 유형만 바꾼 설정 (시뮬레이터용) */
    public GameConfig withPlayerSchoolType(String schoolType) {
        Schools s = new Schools(schools.regions(), schools.types(), schools.defenderTypes(),
                new Schools.PlayerSchool(schoolType, schools.player().region()), schools.nameParts());
        return new GameConfig(rules, trainingMenus, scenes, s, events, clutchMoments);
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
        int topN = rules.match().selection().topStatCount();
        if (topN < 1 || topN > trainedKeys.size()) {
            throw new ConfigException("출전 점수 상위 능력치 개수가 범위를 벗어났습니다: " + topN);
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
        int tiers = rules.traits().tiers().size();
        for (Rules.TraitDef t : rules.traits().list()) {
            validateBonus(t.trainingBonus(), tiers, "특성 " + t.key());
            if (t.semesterWeeklyRate() != null && t.semesterWeeklyRate().size() != tiers) {
                throw new ConfigException("특성 " + t.key() + " 의 단계 수가 맞지 않습니다");
            }
        }
        validateClutchMoments();
        int bondTiers = rules.bonds().tiers().size();
        for (Rules.BondDef b : rules.bonds().axes()) {
            if (b.axis() == null || !b.axis().hasAffinity()) {
                throw new ConfigException("인연 축이 올바르지 않습니다: " + b.axis());
            }
            validateBonus(b.trainingBonus(), bondTiers, "인연 " + b.axis());
            if (b.nightRecovery() != null && b.nightRecovery().size() != bondTiers) {
                throw new ConfigException("인연 " + b.axis() + " 의 단계 수가 맞지 않습니다");
            }
        }
        for (EventDef e : events.events()) {
            if (e.choices() == null || e.choices().size() < 2 || e.choices().size() > 3) {
                throw new ConfigException("이벤트 선택지는 2~3개여야 합니다: " + e.id());
            }
            for (Events.Choice c : e.choices()) {
                if (c.trait() != null && !traitsByKey.containsKey(c.trait())) {
                    throw new ConfigException("이벤트 " + e.id() + ": 알 수 없는 특성 " + c.trait());
                }
                if (c.effects() != null && c.effects().unlockAxis() != null
                        && !c.effects().unlockAxis().lockedAtStart()) {
                    throw new ConfigException("이벤트 " + e.id() + ": 처음부터 열려 있는 축은 열 수 없습니다");
                }
                if (c.effects() != null && c.effects().stat() != null) {
                    c.effects().stat().keySet().forEach(k -> requireStat(k, "이벤트 " + e.id()));
                }
            }
        }
    }

    private void validateClutchMoments() {
        for (ClutchMoments.Style style : ClutchMoments.Style.values()) {
            if (clutchMoments.styles() == null || !clutchMoments.styles().containsKey(style)) {
                throw new ConfigException("승부처 선택지 유형 이름이 없습니다: " + style);
            }
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ClutchMoments.MomentDef m : clutchMoments.moments()) {
            if (!ids.add(m.id())) {
                throw new ConfigException("승부처 id 중복: " + m.id());
            }
            if (m.choices() == null || m.choices().size() < 2 || m.choices().size() > 3) {
                throw new ConfigException("승부처 선택지는 2~3개여야 합니다: " + m.id());
            }
            for (ClutchMoments.MomentChoice c : m.choices()) {
                if (c.stats() == null || c.stats().isEmpty() || c.stats().size() > 2) {
                    throw new ConfigException("승부처 판정 능력치는 1~2개여야 합니다: " + m.id());
                }
                c.stats().forEach(k -> requireStat(k, "승부처 " + m.id()));
                if (c.trait() != null && !traitsByKey.containsKey(c.trait())) {
                    throw new ConfigException("승부처 " + m.id() + ": 알 수 없는 특성 " + c.trait());
                }
                ClutchMoments.Success sc = c.success();
                if (sc.outcome() == ClutchMoments.Outcome.EXTRA_SHOT && !scenesById.containsKey(sc.extraScene())) {
                    throw new ConfigException("승부처 " + m.id() + ": 추가 장면이 없습니다 " + sc.extraScene());
                }
                if (sc.outcome() == ClutchMoments.Outcome.TEAM_GOAL_CHANCE && sc.teamGoalChance() == null) {
                    throw new ConfigException("승부처 " + m.id() + ": 팀 득점 확률이 없습니다");
                }
            }
        }
        // 어떤 전반/후반·스코어 상황에서도 고를 승부처가 있어야 한다
        for (ClutchMoments.Half half : ClutchMoments.Half.values()) {
            for (ClutchMoments.ScoreState score : ClutchMoments.ScoreState.values()) {
                boolean covered = clutchMoments.moments().stream()
                        .anyMatch(m -> m.condition().half() == half && m.condition().score() == score);
                if (!covered) {
                    throw new ConfigException("승부처가 없는 상황: " + half + " " + score);
                }
            }
        }
    }

    public ClutchMoments clutchMoments() {
        return clutchMoments;
    }

    private void validateBonus(Rules.TrainingBonus bonus, int tiers, String where) {
        if (bonus == null) {
            return;
        }
        if (bonus.values() == null || bonus.values().size() != tiers) {
            throw new ConfigException(where + ": 단계별 값의 수가 단계 수와 다릅니다");
        }
        if (bonus.menus() != null) {
            bonus.menus().forEach(m -> {
                if (!menusById.containsKey(m)) {
                    throw new ConfigException(where + ": 알 수 없는 메뉴 " + m);
                }
            });
        }
    }

    public Rules.TraitDef trait(String key) {
        return traitsByKey.get(key);
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
