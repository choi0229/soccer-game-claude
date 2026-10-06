package com.soccergame.domain.competition;

import com.soccergame.domain.config.Rules.Points;
import com.soccergame.domain.random.Rng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 권역 주말리그: 홈·원정 2회전. */
public final class League {
    public record Fixture(int homeId, int awayId) {
    }

    public static final class Row {
        public final int teamId;
        public int played;
        public int won;
        public int drawn;
        public int lost;
        public int goalsFor;
        public int goalsAgainst;
        public int points;

        Row(int teamId) {
            this.teamId = teamId;
        }

        public int goalDiff() {
            return goalsFor - goalsAgainst;
        }
    }

    /** 승점, 골득실, 다득점, 학교 ID 오름차순 */
    public static final Comparator<Row> STANDING = Comparator.<Row>comparingInt(r -> r.points).reversed()
            .thenComparing(Comparator.<Row>comparingInt(Row::goalDiff).reversed())
            .thenComparing(Comparator.<Row>comparingInt(r -> r.goalsFor).reversed())
            .thenComparingInt(r -> r.teamId);

    private final List<Integer> teamIds;
    private final List<List<Fixture>> rounds;
    private final Map<Integer, Row> rows = new LinkedHashMap<>();
    private final List<List<FixtureResult>> results = new ArrayList<>();

    private League(List<Integer> teamIds, List<List<Fixture>> rounds) {
        this.teamIds = List.copyOf(teamIds);
        this.rounds = rounds;
        teamIds.forEach(id -> rows.put(id, new Row(id)));
    }

    /**
     * 순서를 섞은 뒤 원형 방식으로 1회전을 만들고, 2회전은 홈·원정을 바꿔 반복한다.
     */
    public static League create(List<Integer> teamIds, Rng rng) {
        if (teamIds.size() % 2 != 0) {
            throw new IllegalArgumentException("리그 팀 수는 짝수여야 합니다");
        }
        List<Integer> order = new ArrayList<>(teamIds);
        rng.shuffle(order);
        int n = order.size();
        List<List<Fixture>> first = new ArrayList<>();
        for (int r = 0; r < n - 1; r++) {
            List<Fixture> round = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                int a = order.get(i);
                int b = order.get(n - 1 - i);
                boolean swap = (i == 0) ? r % 2 == 1 : i % 2 == 1;
                round.add(swap ? new Fixture(b, a) : new Fixture(a, b));
            }
            first.add(round);
            // 첫 팀을 고정하고 나머지를 한 칸씩 돌린다
            order.add(1, order.remove(n - 1));
        }
        List<List<Fixture>> all = new ArrayList<>(first);
        for (List<Fixture> round : first) {
            all.add(round.stream().map(f -> new Fixture(f.awayId(), f.homeId())).toList());
        }
        return new League(order.stream().sorted().toList(), all);
    }

    public List<Integer> teamIds() {
        return teamIds;
    }

    public int roundCount() {
        return rounds.size();
    }

    public int roundsPlayed() {
        return results.size();
    }

    public List<Fixture> fixtures(int round) {
        return rounds.get(round - 1);
    }

    public List<FixtureResult> results(int round) {
        return results.get(round - 1);
    }

    public void record(List<FixtureResult> roundResults, Points points) {
        for (FixtureResult r : roundResults) {
            Row home = rows.get(r.homeId());
            Row away = rows.get(r.awayId());
            apply(home, r.homeGoals(), r.awayGoals(), points);
            apply(away, r.awayGoals(), r.homeGoals(), points);
        }
        results.add(List.copyOf(roundResults));
    }

    private static void apply(Row row, int scored, int conceded, Points points) {
        row.played++;
        row.goalsFor += scored;
        row.goalsAgainst += conceded;
        if (scored > conceded) {
            row.won++;
            row.points += points.win();
        } else if (scored == conceded) {
            row.drawn++;
            row.points += points.draw();
        } else {
            row.lost++;
            row.points += points.loss();
        }
    }

    public List<Row> standings() {
        return rows.values().stream().sorted(STANDING).toList();
    }

    public int rankOf(int teamId) {
        List<Row> table = standings();
        for (int i = 0; i < table.size(); i++) {
            if (table.get(i).teamId == teamId) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("리그에 없는 팀: " + teamId);
    }
}
