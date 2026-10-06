package com.soccergame.domain.engine;

import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.model.MatchRole;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 48주가 끝났을 때(또는 진행 중 현재까지)의 시즌 요약 */
public record SeasonSummary(List<StatChange> stats, Map<MatchRole, Integer> roles, int appearances, int goals,
                            int assists, Double averageRating, int wins, int draws, int losses, int leagueRank,
                            List<String> cupStages, String cupResult, double academics,
                            int makeupWeeks, int makeupDays, double reputation, long money, int injuries,
                            int exclusions, int events) {

    public record StatChange(String key, String name, boolean primary, boolean passive, double start, double end,
                             String startGrade, String endGrade) {
    }

    public static SeasonSummary of(GameConfig config, GameState s) {
        List<StatChange> stats = new ArrayList<>();
        for (String key : config.allStatKeys()) {
            double start = s.initialStats.get(key);
            double end = s.stats.get(key);
            stats.add(new StatChange(key, config.stat(key).name(), config.isPrimary(key),
                    config.passiveStatKeys().contains(key), start, end, config.grade(start), config.grade(end)));
        }
        Map<MatchRole, Integer> roles = new EnumMap<>(MatchRole.class);
        for (MatchRole r : MatchRole.values()) {
            roles.put(r, 0);
        }
        int apps = 0;
        int goals = 0;
        int assists = 0;
        double ratingSum = 0;
        int w = 0;
        int d = 0;
        int l = 0;
        for (MatchRecord m : s.matches) {
            roles.merge(m.role(), 1, Integer::sum);
            goals += m.playerGoals();
            assists += m.playerAssists();
            if (m.rating() != null) {
                apps++;
                ratingSum += m.rating();
            }
            switch (m.result()) {
                case "W" -> w++;
                case "D" -> d++;
                default -> l++;
            }
        }
        String cupResult = cupResult(s);
        return new SeasonSummary(stats, roles, apps, goals, assists,
                apps == 0 ? null : Math.round(ratingSum / apps * 100) / 100.0, w, d, l,
                s.league.rankOf(s.playerSchoolId), List.copyOf(s.cupStagesReached), cupResult, s.academics,
                s.makeupWeeks.size(), s.metrics.makeupDays, s.reputation, s.money, s.metrics.injuries, s.metrics.exclusions,
                s.eventHistory.size());
    }

    private static String cupResult(GameState s) {
        List<MatchRecord> cup = s.matches.stream()
                .filter(m -> m.competition() == com.soccergame.domain.model.Competition.CUP).toList();
        if (cup.isEmpty()) {
            return "미출전";
        }
        MatchRecord last = cup.getLast();
        boolean advanced = last.ourScore() > last.theirScore()
                || (last.ourScore() == last.theirScore() && Boolean.TRUE.equals(last.penaltyWin()));
        if (advanced && "결승".equals(last.roundLabel())) {
            return "우승";
        }
        return advanced ? last.roundLabel() + " 진출" : last.roundLabel() + " 탈락";
    }
}
