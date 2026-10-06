package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.competition.Cup;
import com.soccergame.domain.competition.League;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.Schools;
import com.soccergame.domain.config.TrainingMenus;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.School;
import com.soccergame.domain.model.Stats;
import com.soccergame.domain.random.RandomStreams;
import com.soccergame.domain.random.Rng;

import java.util.ArrayList;
import java.util.List;

/**
 * 새 판을 만든다. 게임 판정 난수를 쓰는 순서: 학교 이름 → 수비수 유형 → 능력치 시작값 → 리그 대진 → 토너먼트 추첨.
 */
final class GameSetup {
    private final GameConfig config;
    private final GameCalendar calendar;
    private final Resources resources;

    GameSetup(GameConfig config, GameCalendar calendar, Resources resources) {
        this.config = config;
        this.calendar = calendar;
        this.resources = resources;
    }

    GameState create(long seed) {
        Rng rng = RandomStreams.gameStream(seed);
        GameState s = new GameState(seed, rng, calendar.weeksPerYear());
        createSchools(s, rng);
        createPlayer(s, rng);
        League league = League.create(
                s.schools.stream().filter(sc -> sc.region() == s.playerSchool().region()).map(School::id).toList(),
                rng);
        if (league.roundCount() != calendar.leagueRounds()) {
            throw new IllegalStateException("리그 라운드 수(" + league.roundCount() + ")가 일정("
                    + calendar.leagueRounds() + ")과 다릅니다");
        }
        s.league = league;
        s.cup = Cup.draw(s.schools.stream().map(School::id).toList(), rng);
        if (s.cup.alive().size() != 1 << calendar.cupRounds()) {
            throw new IllegalStateException("토너먼트 참가 학교 수가 라운드 수와 맞지 않습니다");
        }
        return s;
    }

    private void createSchools(GameState s, Rng rng) {
        Schools sc = config.schools();
        List<String> names = new ArrayList<>();
        for (String first : sc.nameParts().first()) {
            for (String second : sc.nameParts().second()) {
                names.add(first + second);
            }
        }
        rng.shuffle(names);
        int regions = sc.regions().size();
        int id = 1;
        for (Schools.RegionDef region : sc.regions()) {
            for (Schools.SchoolTypeDef type : sc.types()) {
                for (int i = 0; i < type.count() / regions; i++) {
                    String name = type.nameFormat().replace("{name}", names.get(id - 1));
                    String defender = rng.pick(sc.defenderTypes()).key();
                    s.schools.add(new School(id, name, type.key(), type.name(), region.id(), type.strength(), defender));
                    id++;
                }
            }
        }
        s.playerSchoolId = s.schools.stream()
                .filter(x -> x.region() == sc.player().region() && x.typeKey().equals(sc.player().schoolType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("플레이어 학교를 찾을 수 없습니다"))
                .id();
    }

    private void createPlayer(GameState s, Rng rng) {
        Rules rules = config.rules();
        Stats stats = new Stats(rules.stats().min(), rules.stats().max());
        Rules.IntRange trained = rules.stats().start().trained();
        for (String key : config.trainedStatKeys()) {
            double bonus = config.isPrimary(key) ? rules.archetype().primaryStartBonus() : 0;
            stats.set(key, rng.between(trained.min(), trained.max()) + bonus);
        }
        Rules.IntRange passive = rules.stats().start().passive();
        for (String key : config.passiveStatKeys()) {
            double bonus = config.isPrimary(key) ? rules.archetype().primaryStartBonus() : 0;
            stats.set(key, rng.between(passive.min(), passive.max()) + bonus);
        }
        s.stats = stats;
        s.initialStats = stats.copy();
        s.stamina = resources.maxStamina(s);
        s.condition = rules.condition().start();
        s.money = rules.resources().money();
        s.reputation = rules.resources().reputation();
        s.academics = rules.academics().start();
        for (Axis axis : Axis.values()) {
            if (axis.hasAffinity()) {
                s.affinity.put(axis, config.initialAffinity(axis));
            }
        }
        s.selections.dawn = rules.daily().defaultDawn();
        s.selections.classAttitude = rules.daily().defaultClassAttitude();
        TrainingMenus menus = config.trainingMenus();
        s.selections.menus.putAll(menus.defaults());
        menus.menus().forEach(m -> s.menuTrainingCounts.put(m.id(), 0));
    }
}
