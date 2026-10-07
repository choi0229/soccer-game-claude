package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.competition.Cup;
import com.soccergame.domain.competition.FixtureResult;
import com.soccergame.domain.competition.League;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.match.MatchEngine;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.match.MatchSession;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.Competition;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.School;

import java.util.Arrays;
import java.util.List;

/**
 * 리그 라운드와 토너먼트 라운드를 진행하고, 플레이어 경기 결과를 상태에 적용한다.
 * 한 라운드는 다른 학교 경기를 먼저 계산하고 플레이어 경기를 마지막에 계산한다.
 * 플레이어 경기가 승부처에서 멈추면 라운드 기록은 경기가 끝날 때(complete) 한다.
 */
final class Competitions {
    private final GameConfig config;
    private final Rules rules;
    private final Resources resources;
    private final MatchEngine engine;

    Competitions(GameConfig config, GameCalendar calendar, Resources resources, MatchEngine engine) {
        this.config = config;
        this.rules = config.rules();
        this.resources = resources;
        this.engine = engine;
    }

    /** 라운드 진행 결과. pending 이 있으면 승부처 선택을 기다린다. */
    record RoundOutcome(MatchRecord record, PendingMatch pending) {
        boolean paused() {
            return pending != null;
        }
    }

    RoundOutcome playLeagueRound(GameState s, int round, String dateLabel) {
        return playRound(s, Competition.LEAGUE, round, round + "라운드", s.league.fixtures(round), false, dateLabel);
    }

    /** 토너먼트 1라운드. 플레이어 학교가 탈락한 뒤에도 나머지 대진은 계속 진행한다. */
    RoundOutcome playCupRound(GameState s, int roundIndex, String dateLabel) {
        if (s.cup.roundsPlayed() != roundIndex) {
            throw new IllegalStateException("토너먼트 라운드 순서가 어긋났습니다: " + roundIndex);
        }
        List<League.Fixture> pairs = s.cup.currentPairs();
        return playRound(s, Competition.CUP, roundIndex, Cup.roundName(pairs.size() * 2), pairs, true, dateLabel);
    }

    private RoundOutcome playRound(GameState s, Competition competition, int round, String roundLabel,
                                   List<League.Fixture> fixtures, boolean knockout, String dateLabel) {
        FixtureResult[] results = new FixtureResult[fixtures.size()];
        int playerIndex = -1;
        for (int i = 0; i < fixtures.size(); i++) {
            League.Fixture f = fixtures.get(i);
            if (f.homeId() == s.playerSchoolId || f.awayId() == s.playerSchoolId) {
                playerIndex = i;
            } else {
                results[i] = simulate(s, f, knockout);
            }
        }
        if (playerIndex < 0) {
            record(s, competition, results, false);
            return new RoundOutcome(null, null);
        }
        League.Fixture f = fixtures.get(playerIndex);
        boolean home = f.homeId() == s.playerSchoolId;
        School us = s.playerSchool();
        School them = s.school(home ? f.awayId() : f.homeId());
        double score = engine.selectionScore(s.affinity.get(Axis.COACH), s.stats);
        MatchRole role = engine.role(score, us.strength(), s.isInjured());
        var defender = config.defenderType(them.defenderType());
        MatchSession session = engine.start(s.rng, new MatchEngine.Input(s.stats, role,
                resources.conditionLevel(s).match(), us.strength(), them.strength(), defender.passive(),
                defender.name(), knockout, true));
        PendingMatch pending = new PendingMatch(session, competition, round, roundLabel, them, home, defender, role,
                Math.round(score * 10) / 10.0, dateLabel, fixtures, results, playerIndex);
        if (session.awaitingChoice()) {
            return new RoundOutcome(null, pending);
        }
        return new RoundOutcome(complete(s, pending), null);
    }

    /** 멈춘 경기의 승부처 선택을 판정하고 경기를 마무리한다. */
    MatchRecord resolve(GameState s, PendingMatch pending, int choiceIndex) {
        engine.resolveClutch(s.rng, pending.session, choiceIndex);
        return complete(s, pending);
    }

    /** 끝난 플레이어 경기를 적용하고 라운드를 기록한다. */
    private MatchRecord complete(GameState s, PendingMatch p) {
        MatchEngine.Result r = p.session.result();
        Rules.MatchRules mr = rules.match();
        MatchRole role = p.role;
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
                    p.defender.passive());
            growth = s.rng.pick(candidates);
            s.stats.add(growth, mr.passive().growthPerMatch());
            if (r.clutch() != null) {
                String trait = config.clutchMoments().moments().stream()
                        .filter(m -> m.id().equals(r.clutch().momentId())).findFirst().orElseThrow()
                        .choices().get(r.clutch().choiceIndex()).trait();
                if (trait != null) {
                    s.traitScores.merge(trait, 1, Integer::sum);
                }
            }
        }
        resources.stamina(s, mr.staminaCost().of(role));

        MatchRecord record = new MatchRecord(p.competition, p.roundLabel, s.week, p.dateLabel, p.opponent.id(),
                p.opponent.name(), p.opponent.strength(), p.defender.name(), p.home, role, p.selectionScore,
                r.teamGoals(), r.opponentGoals(), r.playerGoals(), r.playerAssists(), r.pressGoals(),
                r.clutchTeamGoals(), r.ourScore(), r.theirScore(), r.penaltyWin(), r.outcome(), rating, reputation,
                growth == null ? null : config.stat(growth).name(),
                growth == null ? null : mr.passive().growthPerMatch(), r.scenes(), r.timeline(), r.clutch());
        s.matches.add(record);

        League.Fixture f = p.fixtures.get(p.playerIndex);
        Boolean pk = r.penaltyWin();
        p.results[p.playerIndex] = p.home
                ? new FixtureResult(f.homeId(), f.awayId(), r.ourScore(), r.theirScore(), pk)
                : new FixtureResult(f.homeId(), f.awayId(), r.theirScore(), r.ourScore(), pk == null ? null : !pk);
        record(s, p.competition, p.results, true);
        return record;
    }

    private void record(GameState s, Competition competition, FixtureResult[] results, boolean playerPlayed) {
        List<FixtureResult> list = Arrays.asList(results);
        if (competition == Competition.LEAGUE) {
            s.league.record(list, rules.match().points());
            return;
        }
        s.cup.record(list);
        if (playerPlayed && s.cup.isAlive(s.playerSchoolId)) {
            int left = s.cup.alive().size();
            for (Rules.CupStage stage : rules.reputation().cupStages()) {
                if (stage.teamsLeft() == left) {
                    resources.reputation(s, stage.value() * rules.reputation().gradeMultiplier());
                    s.cupStagesReached.add(stage.name());
                }
            }
        }
    }

    private FixtureResult simulate(GameState s, League.Fixture f, boolean knockout) {
        int[] goals = engine.simulateTeams(s.rng, s.school(f.homeId()).strength(), s.school(f.awayId()).strength());
        Boolean pk = null;
        if (knockout && goals[0] == goals[1]) {
            pk = s.rng.chance(rules.match().penaltyWinChance());
        }
        return new FixtureResult(f.homeId(), f.awayId(), goals[0], goals[1], pk);
    }
}
