package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.competition.Cup;
import com.soccergame.domain.competition.FixtureResult;
import com.soccergame.domain.competition.League;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.match.MatchEngine;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.Competition;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.School;

import java.util.ArrayList;
import java.util.List;

/** 리그 라운드와 토너먼트 라운드를 진행하고, 플레이어 경기 결과를 상태에 적용한다. */
final class Competitions {
    private final GameConfig config;
    private final Rules rules;
    private final GameCalendar calendar;
    private final Resources resources;
    private final MatchEngine engine;

    Competitions(GameConfig config, GameCalendar calendar, Resources resources, MatchEngine engine) {
        this.config = config;
        this.rules = config.rules();
        this.calendar = calendar;
        this.resources = resources;
        this.engine = engine;
    }

    /** 권역 리그 1라운드(플레이어 학교 경기 포함)를 치르고 플레이어 경기 기록을 돌려준다. */
    MatchRecord playLeagueRound(GameState s, int round, String dateLabel) {
        List<FixtureResult> results = new ArrayList<>();
        MatchRecord record = null;
        for (League.Fixture f : s.league.fixtures(round)) {
            if (f.homeId() == s.playerSchoolId || f.awayId() == s.playerSchoolId) {
                boolean home = f.homeId() == s.playerSchoolId;
                int opponent = home ? f.awayId() : f.homeId();
                record = playPlayerMatch(s, Competition.LEAGUE, round + "라운드", opponent, home, false, dateLabel);
                results.add(home
                        ? new FixtureResult(f.homeId(), f.awayId(), record.ourScore(), record.theirScore(), null)
                        : new FixtureResult(f.homeId(), f.awayId(), record.theirScore(), record.ourScore(), null));
            } else {
                results.add(simulate(s, f, false));
            }
        }
        s.league.record(results, rules.match().points());
        return record;
    }

    /**
     * 토너먼트 1라운드를 치른다. 플레이어 학교가 탈락한 뒤에도 나머지 대진은 계속 진행한다.
     * 플레이어 학교 경기가 없으면 null.
     */
    MatchRecord playCupRound(GameState s, int roundIndex, String dateLabel) {
        if (s.cup.roundsPlayed() != roundIndex) {
            throw new IllegalStateException("토너먼트 라운드 순서가 어긋났습니다: " + roundIndex);
        }
        List<League.Fixture> pairs = s.cup.currentPairs();
        String roundName = Cup.roundName(pairs.size() * 2);
        List<FixtureResult> results = new ArrayList<>();
        MatchRecord record = null;
        for (League.Fixture f : pairs) {
            if (f.homeId() == s.playerSchoolId || f.awayId() == s.playerSchoolId) {
                boolean home = f.homeId() == s.playerSchoolId;
                int opponent = home ? f.awayId() : f.homeId();
                record = playPlayerMatch(s, Competition.CUP, roundName, opponent, home, true, dateLabel);
                Boolean pk = record.penaltyWin();
                results.add(home
                        ? new FixtureResult(f.homeId(), f.awayId(), record.ourScore(), record.theirScore(), pk)
                        : new FixtureResult(f.homeId(), f.awayId(), record.theirScore(), record.ourScore(),
                        pk == null ? null : !pk));
            } else {
                results.add(simulate(s, f, true));
            }
        }
        s.cup.record(results);
        if (record != null && s.cup.isAlive(s.playerSchoolId)) {
            int left = s.cup.alive().size();
            for (Rules.CupStage stage : rules.reputation().cupStages()) {
                if (stage.teamsLeft() == left) {
                    resources.reputation(s, stage.value() * rules.reputation().gradeMultiplier());
                    s.cupStagesReached.add(stage.name());
                }
            }
        }
        return record;
    }

    private FixtureResult simulate(GameState s, League.Fixture f, boolean knockout) {
        int[] goals = engine.simulateTeams(s.rng, s.school(f.homeId()).strength(), s.school(f.awayId()).strength());
        Boolean pk = null;
        if (knockout && goals[0] == goals[1]) {
            pk = s.rng.chance(rules.match().penaltyWinChance());
        }
        return new FixtureResult(f.homeId(), f.awayId(), goals[0], goals[1], pk);
    }

    private MatchRecord playPlayerMatch(GameState s, Competition competition, String roundLabel, int opponentId,
                                        boolean home, boolean knockout, String dateLabel) {
        School us = s.playerSchool();
        School them = s.school(opponentId);
        Rules.MatchRules mr = rules.match();
        double score = engine.selectionScore(s.affinity.get(Axis.COACH), s.stats);
        MatchRole role = engine.role(score, us.strength(), s.isInjured());
        var defender = config.defenderType(them.defenderType());
        MatchEngine.Result r = engine.play(s.rng, new MatchEngine.Input(s.stats, role,
                resources.conditionLevel(s).match(), us.strength(), them.strength(), defender.passive(),
                defender.name(), knockout));

        Double rating = null;
        double reputation = 0;
        String growth = null;
        if (role.plays()) {
            rating = engine.rating(r);
            Rules.CoachFeedback cf = mr.coachFeedback();
            if (rating >= cf.goodFrom()) {
                resources.affinity(s, Axis.COACH, cf.goodDelta());
            } else if (rating < cf.badBelow()) {
                resources.affinity(s, Axis.COACH, cf.badDelta());
            }
            Rules.ReputationRules rep = rules.reputation();
            double raw = r.playerGoals() * rep.goal() + r.playerAssists() * rep.assist();
            for (Rules.RatingReputation tier : rep.rating()) {
                if (rating >= tier.from()) {
                    raw += tier.value();
                    break;
                }
            }
            reputation = raw * rep.gradeMultiplier();
            resources.reputation(s, reputation);
            List<String> candidates = List.of(GameConfig.MOMENTUM, GameConfig.CLUTCH, GameConfig.MENTAL,
                    defender.passive());
            growth = s.rng.pick(candidates);
            s.stats.add(growth, mr.passive().growthPerMatch());
        }
        resources.stamina(s, mr.staminaCost().of(role));

        String result = r.outcome();
        MatchRecord record = new MatchRecord(competition, roundLabel, s.week, dateLabel, them.id(), them.name(),
                them.strength(), defender.name(), home, role, Math.round(score * 10) / 10.0, r.teamGoals(),
                r.opponentGoals(), r.playerGoals(), r.playerAssists(), r.pressGoals(), r.ourScore(), r.theirScore(),
                r.penaltyWin(), result, rating, reputation, growth == null ? null : config.stat(growth).name(),
                r.scenes(), r.timeline());
        s.matches.add(record);
        return record;
    }
}
