package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.competition.Cup;
import com.soccergame.domain.config.Rules.TournamentDef;
import com.soccergame.domain.match.MatchRecord;

import java.util.ArrayList;
import java.util.List;

/** 대회 하나에서 플레이어 학교의 성적 */
public record TournamentResult(String key, String name, String period, Stage stage, String text, int matches) {

    public enum Stage {
        UPCOMING("예정"),
        IN_PROGRESS("진행 중"),
        NOT_ENTERED("불참"),
        EARLY_EXIT("8강 전 탈락"),
        QUARTER_FINAL("8강"),
        SEMI_FINAL("4강"),
        RUNNER_UP("준우승"),
        CHAMPION("우승");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** 최종 성적인지 (예정·진행 중이 아님) */
        public boolean isFinal() {
            return this != UPCOMING && this != IN_PROGRESS;
        }
    }

    public static List<TournamentResult> of(GameCalendar calendar, GameState s) {
        List<TournamentResult> list = new ArrayList<>();
        for (TournamentDef t : calendar.tournaments()) {
            list.add(of(calendar, s, t));
        }
        return list;
    }

    public static TournamentResult of(GameCalendar calendar, GameState s, TournamentDef t) {
        String period = calendar.periodLabel(t.period());
        List<MatchRecord> matches = s.matches.stream().filter(m -> t.key().equals(m.tournamentKey())).toList();
        Cup cup = s.cups.get(t.key());
        if (cup == null) {
            return new TournamentResult(t.key(), t.name(), period, Stage.UPCOMING, "예정", 0);
        }
        if (!cup.entrants().contains(s.playerSchoolId)) {
            return new TournamentResult(t.key(), t.name(), period, Stage.NOT_ENTERED, "불참 (진출 실패)", 0);
        }
        if (matches.isEmpty()) {
            return new TournamentResult(t.key(), t.name(), period, Stage.UPCOMING, "대진 추첨 완료", 0);
        }
        MatchRecord last = matches.getLast();
        boolean advanced = last.ourScore() > last.theirScore()
                || (last.ourScore() == last.theirScore() && Boolean.TRUE.equals(last.penaltyWin()));
        String round = last.roundLabel();
        if (advanced) {
            if (round.equals("결승")) {
                return new TournamentResult(t.key(), t.name(), period, Stage.CHAMPION, "우승", matches.size());
            }
            return new TournamentResult(t.key(), t.name(), period, Stage.IN_PROGRESS, round + " 통과",
                    matches.size());
        }
        Stage stage = switch (round) {
            case "결승" -> Stage.RUNNER_UP;
            case "4강" -> Stage.SEMI_FINAL;
            case "8강" -> Stage.QUARTER_FINAL;
            default -> Stage.EARLY_EXIT;
        };
        String text = stage == Stage.RUNNER_UP ? "준우승" : round + " 탈락";
        return new TournamentResult(t.key(), t.name(), period, stage, text, matches.size());
    }
}
