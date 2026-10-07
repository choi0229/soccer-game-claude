package com.soccergame.domain.engine;

import com.soccergame.domain.competition.FixtureResult;
import com.soccergame.domain.competition.League;
import com.soccergame.domain.config.Schools.DefenderTypeDef;
import com.soccergame.domain.match.MatchSession;
import com.soccergame.domain.model.Competition;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.School;

import java.util.List;

/**
 * 승부처 선택을 기다리며 멈춘 플레이어 경기와, 경기 뒤에 이어서 처리할 그날의 나머지.
 * results 는 대진 순서대로의 결과이며 플레이어 경기 자리(playerIndex)는 경기가 끝나면 채운다.
 */
public final class PendingMatch {
    /** 경기가 끝난 뒤 이어서 처리할 하루 */
    public enum DayKind {
        WEEKDAY, SATURDAY
    }

    public final MatchSession session;
    public final Competition competition;
    /** 토너먼트면 그 대회 (리그면 null) */
    public final com.soccergame.domain.config.Rules.TournamentDef tournament;
    public final int round;
    public final String roundLabel;
    public final School opponent;
    public final boolean home;
    public final DefenderTypeDef defender;
    public final MatchRole role;
    public final double selectionScore;
    public final String dateLabel;
    final List<League.Fixture> fixtures;

    public String competitionName() {
        return tournament == null ? competition.label() : tournament.name();
    }
    final FixtureResult[] results;
    final int playerIndex;

    DayKind dayKind;
    List<LogEntry> log;
    List<String> newEvents;

    PendingMatch(MatchSession session, Competition competition,
                 com.soccergame.domain.config.Rules.TournamentDef tournament, int round, String roundLabel, School opponent,
                 boolean home, DefenderTypeDef defender, MatchRole role, double selectionScore, String dateLabel,
                 List<League.Fixture> fixtures, FixtureResult[] results, int playerIndex) {
        this.session = session;
        this.competition = competition;
        this.tournament = tournament;
        this.round = round;
        this.roundLabel = roundLabel;
        this.opponent = opponent;
        this.home = home;
        this.defender = defender;
        this.role = role;
        this.selectionScore = selectionScore;
        this.dateLabel = dateLabel;
        this.fixtures = fixtures;
        this.results = results;
        this.playerIndex = playerIndex;
    }
}
