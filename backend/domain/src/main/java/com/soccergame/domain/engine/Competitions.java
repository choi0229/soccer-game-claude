package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.competition.Cup;
import com.soccergame.domain.competition.FixtureResult;
import com.soccergame.domain.competition.League;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.Rules.TournamentDef;
import com.soccergame.domain.match.MatchEngine;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.match.MatchSession;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.Competition;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.School;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 리그 라운드와 토너먼트 라운드를 진행하고, 플레이어 경기 결과를 상태에 적용한다.
 * 한 라운드는 다른 학교 경기(리그는 다른 권역 포함)를 먼저 계산하고 플레이어 경기를 마지막에 계산한다.
 * 플레이어 경기가 승부처에서 멈추면 라운드 기록은 경기가 끝날 때(complete) 한다.
 */
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

    /** 라운드 진행 결과. pending 이 있으면 승부처 선택을 기다린다. */
    record RoundOutcome(MatchRecord record, PendingMatch pending) {
        boolean paused() {
            return pending != null;
        }
    }

    /**
     * 리그 1라운드. 다른 권역 리그를 먼저 계산하고(결과만), 플레이어 권역은 플레이어 경기를 마지막에 계산한다.
     */
    RoundOutcome playLeagueRound(GameState s, int round, String dateLabel) {
        for (Map.Entry<Integer, League> e : s.leagues.entrySet()) {
            if (e.getValue() == s.league) {
                continue;
            }
            List<League.Fixture> fixtures = e.getValue().fixtures(round);
            FixtureResult[] results = new FixtureResult[fixtures.size()];
            for (int i = 0; i < fixtures.size(); i++) {
                results[i] = simulate(s, fixtures.get(i), false);
            }
            e.getValue().record(Arrays.asList(results), rules.match().points());
        }
        return playRound(s, Competition.LEAGUE, null, round, round + "라운드", s.league.fixtures(round), false,
                dateLabel);
    }

    /** 토너먼트 1라운드. 플레이어 학교가 탈락했거나 참가하지 않아도 나머지 대진은 진행한다. */
    RoundOutcome playCupRound(GameState s, TournamentDef t, int roundIndex, String dateLabel) {
        Cup cup = s.cups.get(t.key());
        if (cup == null) {
            cup = draw(s, t);
        }
        if (cup.roundsPlayed() != roundIndex) {
            throw new IllegalStateException(t.name() + " 라운드 순서가 어긋났습니다: " + roundIndex);
        }
        List<League.Fixture> pairs = cup.currentPairs();
        return playRound(s, Competition.CUP, t, roundIndex, Cup.roundName(pairs.size() * 2), pairs, true,
                dateLabel);
    }

    /** 이번 주에 첫 경기가 있는 대회의 대진을 추첨한다 (주가 시작할 때) */
    void drawTournamentsStartingIn(GameState s, int week) {
        for (TournamentDef t : calendar.tournaments()) {
            if (calendar.firstWeek(t) == week && !s.cups.containsKey(t.key())) {
                draw(s, t);
            }
        }
    }

    private Cup draw(GameState s, TournamentDef t) {
        Cup cup = Cup.draw(entrants(s, t), s.rng);
        if (cup.alive().size() != t.teams()) {
            throw new IllegalStateException(t.name() + " 참가 학교 수가 라운드 수와 맞지 않습니다: " + cup.alive().size());
        }
        s.cups.put(t.key(), cup);
        return cup;
    }

    /** 참가 학교: 전체, 또는 권역 리그 최종 순위 상위 N개교씩 (권역 순서) */
    List<Integer> entrants(GameState s, TournamentDef t) {
        if (t.entry().rule() == Rules.EntryRule.ALL) {
            return s.schools.stream().map(School::id).toList();
        }
        List<Integer> ids = new ArrayList<>();
        for (League league : s.leagues.values()) {
            league.standings().stream().limit(t.entry().topPerRegion()).forEach(r -> ids.add(r.teamId));
        }
        return ids;
    }

    /** 플레이어 학교가 이 대회에서 아직 경기가 남았는지 (추첨 전이면 참가 규칙으로 판단) */
    boolean playerAlive(GameState s, TournamentDef t) {
        Cup cup = s.cups.get(t.key());
        if (cup != null) {
            return cup.isAlive(s.playerSchoolId);
        }
        if (t.entry().rule() == Rules.EntryRule.ALL) {
            return true;
        }
        return s.league.roundsPlayed() == s.league.roundCount() && entrants(s, t).contains(s.playerSchoolId);
    }

    private RoundOutcome playRound(GameState s, Competition competition, TournamentDef tournament, int round,
                                   String roundLabel, List<League.Fixture> fixtures, boolean knockout,
                                   String dateLabel) {
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
            record(s, tournament, results, false);
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
        PendingMatch pending = new PendingMatch(session, competition, tournament, round, roundLabel, them, home,
                defender, role, Math.round(score * 10) / 10.0, dateLabel, fixtures, results, playerIndex);
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

        MatchRecord record = new MatchRecord(p.competition, p.competitionName(),
                p.tournament == null ? null : p.tournament.key(), p.roundLabel, s.week, p.dateLabel, p.opponent.id(),
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
        record(s, p.tournament, p.results, true);
        return record;
    }

    /** 라운드 결과 기록: 리그(tournament == null)는 플레이어 권역 순위표, 토너먼트는 대진과 평판 */
    private void record(GameState s, TournamentDef tournament, FixtureResult[] results, boolean playerPlayed) {
        List<FixtureResult> list = Arrays.asList(results);
        if (tournament == null) {
            s.league.record(list, rules.match().points());
            return;
        }
        Cup cup = s.cups.get(tournament.key());
        cup.record(list);
        if (playerPlayed && cup.isAlive(s.playerSchoolId)) {
            int left = cup.alive().size();
            for (Rules.CupStage stage : rules.reputation().cupStages()) {
                if (stage.teamsLeft() == left) {
                    resources.reputation(s, stage.value() * rules.reputation().gradeMultiplier());
                    s.cupStagesReached.computeIfAbsent(tournament.key(), k -> new ArrayList<>()).add(stage.name());
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
