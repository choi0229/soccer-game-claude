package com.soccergame.simulator;

import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.engine.TournamentResult;
import com.soccergame.domain.model.MatchRole;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** 결과를 마크다운 표로 만든다. */
public final class ReportPrinter {
    private final GameConfig config;
    private final StringBuilder out = new StringBuilder();

    public ReportPrinter(GameConfig config) {
        this.config = config;
    }

    public String render(List<StrategyReport> reports, int runs, long seedStart) {
        line("# 밸런스 시뮬레이터 결과");
        line("");
        line("- 전략별 " + runs + "회, 시드 " + seedStart + " ~ " + (seedStart + runs - 1) + " (전략끼리 같은 시드 사용)");
        line("- 분포 표기: 평균 (표준편차) / 최소 · p10 · p50 · p90 · 최대");
        line("");

        section("1. 1년 뒤 능력치");
        header("항목", reports);
        distRow("주력 3종 평균", reports, r -> r.primaryAvg);
        distRow("주력 3종 1년 상승량", reports, r -> r.primaryGain);
        distRow("훈련 능력치 12종 평균", reports, r -> r.trainedAvg);
        distRow("훈련 능력치 1년 상승량", reports, r -> r.trainedGain);
        distRow("패시브 6종 평균", reports, r -> r.passiveAvg);
        line("");
        line("능력치별 1년 뒤 평균 (등급)");
        line("");
        header("능력치", reports);
        for (String key : config.allStatKeys()) {
            String name = config.stat(key).name() + (config.isPrimary(key) ? " ★" : "");
            row(name, reports, r -> {
                double mean = StrategyReport.arr(r.finalStats.get(key)).length == 0 ? 0
                        : r.finalStats.get(key).stream().mapToDouble(Double::doubleValue).average().orElse(0);
                return f1(mean) + " (" + config.grade(mean) + ")";
            });
        }
        line("");

        section("2. 체력, 훈련 제외, 부상");
        header("항목", reports);
        distRow("훈련 제외 횟수/년", reports, r -> r.exclusions);
        distRow("부상 횟수/년", reports, r -> r.injuries);
        line("");
        line("주별 평균 체력 (하루 끝, 밤 회복 전 기준)");
        line("");
        StringBuilder head = new StringBuilder("| 주 |");
        StringBuilder sep = new StringBuilder("|---|");
        for (StrategyReport r : reports) {
            head.append(' ').append(r.strategy).append(" |");
            sep.append("---:|");
        }
        line(head.toString());
        line(sep.toString());
        int weeks = reports.getFirst().weeklyStamina.length;
        int perMonth = config.rules().calendar().weeksPerMonth();
        for (int w = 0; w < weeks; w++) {
            int month = Math.floorMod(config.rules().calendar().startMonth() - 1 + w / perMonth, 12) + 1;
            StringBuilder sb = new StringBuilder("| " + (w + 1) + " (" + month + "월 " + (w % perMonth + 1) + "주) |");
            for (StrategyReport r : reports) {
                sb.append(' ').append(f1(r.weeklyStamina[w] / r.runs)).append(" |");
            }
            line(sb.toString());
        }
        line("");

        section("3. 학업");
        header("항목", reports);
        distRow("1년 뒤 학업 성취", reports, r -> r.academics);
        distRow("나머지 공부 주 수/년", reports, r -> r.makeupWeeks);
        distRow("실제 나머지 공부 일 수/년 (경기일 제외)", reports, r -> r.makeupDays);
        row("나머지 공부 1주 이상 판 비율", reports, r -> pct(r.makeupRuns, r.runs));
        line("");

        section("4. 경기");
        header("항목", reports);
        row("플레이어 학교 경기 수/년", reports, r -> f2((double) r.matches / r.runs));
        for (MatchRole role : MatchRole.values()) {
            row(role.label() + " 비율", reports, r -> pct(r.roles.get(role), r.matches));
        }
        distRow("첫 선발 주차 (99=없음)", reports, r -> r.starterFirstWeek);
        row("출전 경기당 장면 수", reports, r -> f2(div(r.scenes, r.appearances)));
        row("출전 경기당 골", reports, r -> f2(div(r.goals, r.appearances)));
        row("출전 경기당 도움", reports, r -> f2(div(r.assists, r.appearances)));
        row("출전 경기당 압박 득점", reports, r -> f2(div(r.pressGoals, r.appearances)));
        distRow("시즌 골", reports, r -> r.seasonGoals);
        distRow("시즌 도움", reports, r -> r.seasonAssists);
        row("평균 평점 (출전 경기)", reports, r -> f2(div(r.ratingSum, r.appearances)));
        row("경기당 팀 득점 (최종 스코어)", reports, r -> f2(div(r.ourScore, r.matches)));
        row("경기당 팀 득점 중 포아송분", reports, r -> f2(div(r.teamPoissonGoals, r.matches)));
        row("경기당 실점", reports, r -> f2(div(r.theirScore, r.matches)));
        distRow("1년 뒤 감독 관계도", reports, r -> r.coachAffinity);
        line("");

        section("5. 평판, 순위");
        header("항목", reports);
        distRow("1년 뒤 평판", reports, r -> r.reputation);
        for (int rank = 1; rank <= 8; rank++) {
            int k = rank;
            row("리그 " + rank + "위", reports, r -> pct(r.leagueRank[k], r.runs));
        }
        line("");
        section("5-1. 대회별 최종 성적 (판 비율)");
        header("대회 · 성적", reports);
        var stages = List.of(TournamentResult.Stage.NOT_ENTERED, TournamentResult.Stage.EARLY_EXIT,
                TournamentResult.Stage.QUARTER_FINAL, TournamentResult.Stage.SEMI_FINAL,
                TournamentResult.Stage.RUNNER_UP, TournamentResult.Stage.CHAMPION);
        for (var t : config.rules().calendar().tournaments()) {
            for (var stage : stages) {
                if (stage == TournamentResult.Stage.NOT_ENTERED && t.entry().rule() == Rules.EntryRule.ALL) {
                    continue;
                }
                row(t.name() + " · " + stage.label(), reports, r -> pct(
                        r.tournamentStage.getOrDefault(t.key(), Map.of()).getOrDefault(stage, 0), r.runs));
            }
            if (t.entry().rule() == Rules.EntryRule.LEAGUE_TOP) {
                row(t.name() + " 진출 비율", reports, r -> pct(r.runs
                        - r.tournamentStage.getOrDefault(t.key(), Map.of()).getOrDefault(TournamentResult.Stage.NOT_ENTERED, 0),
                        r.runs));
            }
        }
        line("");
        section("5-2. 월별 출전과 평점");
        header("달", reports);
        int start = config.rules().calendar().startMonth();
        for (int i = 0; i < 12; i++) {
            int month = Math.floorMod(start - 1 + i, 12) + 1;
            row(month + "월 경기 수/판", reports, r -> {
                int[] c = r.monthRoles.get(month);
                return c == null ? "0" : f2((double) java.util.Arrays.stream(c).sum() / r.runs);
            });
            row(month + "월 선발/교체/벤치", reports, r -> roleSplit(r.monthRoles.get(month)));
            row(month + "월 평균 평점", reports, r -> {
                int[] c = r.monthRoles.get(month);
                int apps = c == null ? 0 : c[0] + c[1];
                return apps == 0 ? "-" : f2(r.monthRatingSum.getOrDefault(month, 0.0) / apps);
            });
        }
        line("");

        section("6. 기타");
        header("항목", reports);
        distRow("이벤트 발생 수/년", reports, r -> r.eventsPerRun);
        distRow("1년 뒤 돈(원)", reports, r -> r.money);
        line("");
        section("7. 특성, 인연, 성장 보너스");
        header("항목", reports);
        int traitTierCount = config.rules().traits().tiers().size();
        for (var trait : config.rules().traits().list()) {
            row(trait.name() + " 점수 평균", reports,
                    r -> f1((double) r.traitScoreSum.getOrDefault(trait.key(), 0L) / r.runs));
            row(trait.name() + " 단계 0/1/2/3", reports, r -> {
                int[] t = r.traitTiers.get(trait.key());
                List<String> parts = new java.util.ArrayList<>();
                for (int i = 0; i <= traitTierCount; i++) {
                    parts.add(pct(t[i], r.runs));
                }
                return String.join(" / ", parts);
            });
        }
        int bondTierCount = config.rules().bonds().tiers().size();
        for (var bond : config.rules().bonds().axes()) {
            row(bond.axis().label() + " 인연 잠김/0/1/2/3", reports, r -> {
                int[] t = r.bondTiers.get(bond.axis());
                List<String> parts = new java.util.ArrayList<>();
                for (int i = 0; i <= bondTierCount + 1; i++) {
                    parts.add(pct(t[i], r.runs));
                }
                return String.join(" / ", parts);
            });
        }
        row("여자친구 해금 비율", reports, r -> pct((int) r.girlfriendUnlockWeek.stream()
                .filter(w -> w < StrategyReport.NEVER).count(), r.runs));
        row("여자친구 해금 주차 중앙값 (해금된 판)", reports, r -> {
            double[] w = r.girlfriendUnlockWeek.stream().filter(x -> x < StrategyReport.NEVER)
                    .mapToDouble(Double::doubleValue).toArray();
            return w.length == 0 ? "-" : f0(Distribution.of(w).p50()) + "주";
        });
        line("");
        section("7-1. 승부처");
        header("항목", reports);
        row("출전 경기당 승부처 발생 비율", reports, r -> pct(r.clutchMatches, r.appearances));
        row("승부처 전반 / 후반", reports, r -> pct(r.clutchHalf[0], r.clutchMatches) + " / "
                + pct(r.clutchHalf[1], r.clutchMatches));
        for (var style : com.soccergame.domain.config.ClutchMoments.Style.values()) {
            String label = config.clutchMoments().styles().get(style);
            row(label + " 선택 비율", reports, r -> pct(r.clutchStyle.getOrDefault(style.name(), new int[2])[0],
                    r.clutchMatches));
            row(label + " 성공률", reports, r -> {
                int[] c = r.clutchStyle.getOrDefault(style.name(), new int[2]);
                return c[0] == 0 ? "-" : pct(c[1], c[0]);
            });
            row(label + " 평점 변화 평균", reports, r -> {
                int[] c = r.clutchStyle.getOrDefault(style.name(), new int[2]);
                return c[0] == 0 ? "-" : String.format(Locale.ROOT, "%+.3f",
                        r.clutchStyleRating.getOrDefault(style.name(), 0.0) / c[0]);
            });
        }
        for (var moment : config.clutchMoments().moments()) {
            row("발생 비율: " + moment.id() + " " + moment.title() + " (" + halfLabel(moment.condition().half())
                    + "·" + scoreLabel(moment.condition().score()) + ")", reports,
                    r -> pct(r.clutchMoments.getOrDefault(moment.id(), 0), r.clutchMatches));
        }
        row("평균 평점: 승부처 있는 경기", reports, r -> r.clutchMatches == 0 ? "-"
                : f2(r.ratingWithClutch / r.clutchMatches));
        row("평균 평점: 승부처 없는 경기", reports, r -> r.appearancesWithoutClutch == 0 ? "-"
                : f2(r.ratingWithoutClutch / r.appearancesWithoutClutch));
        line("");
        header("항목", reports);
        row("메뉴 훈련 평균 적용 보너스", reports, r -> r.menuTrainings == 0 ? "-"
                : String.format(Locale.ROOT, "+%.1f%%", 100 * r.bonusApplied / r.menuTrainings));
        row("상한에 걸린 훈련 비율", reports, r -> r.menuTrainings == 0 ? "-"
                : String.format(Locale.ROOT, "%.1f%%", 100.0 * r.bonusCapped / r.menuTrainings));
        line("");
        line("이벤트별 판당 평균 발생 횟수 (괄호: 한 판 최대)");
        line("");
        header("이벤트", reports);
        for (var e : config.events().events()) {
            row(e.id() + " " + e.title() + (e.once() ? " (1회)" : ""), reports,
                    r -> f2((double) r.eventCounts.getOrDefault(e.id(), 0) / r.runs) + " ("
                            + r.eventMax.getOrDefault(e.id(), 0) + ")");
        }
        line("");
        return out.toString();
    }

    public record SchoolTypeRow(String typeName, double strength, StrategyReport report) {
    }

    public String renderSchoolTypes(List<SchoolTypeRow> rows, int runs) {
        out.setLength(0);
        section("8. 학교 유형별 출전과 리그 순위 (조합마다 " + runs + "판)");
        line("주차 열: 1년 중 처음으로 그 역할로 출전한 주차의 중앙값 (괄호는 1년 내내 한 번도 없던 판의 비율)");
        line("");
        line("전반기 = 리그 마지막 라운드 주까지, 후반기 = 그 뒤. 칸은 선발/교체/벤치 비율");
        line("");
        line("| 학교 유형 (전력) | 전략 | 선발 | 교체 | 벤치 | 부상 결장 | 전반기 | 후반기 | 첫 교체 출전 주차 | 첫 선발 주차 | 평균 평점 | 평균 순위 | 1위 | 1~4위 | 8위 |");
        line("|---|---|---:|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|");
        for (SchoolTypeRow row : rows) {
            StrategyReport r = row.report();
            double rankSum = 0;
            int top4 = 0;
            for (int rank = 1; rank <= 8; rank++) {
                rankSum += rank * r.leagueRank[rank];
                if (rank <= 4) {
                    top4 += r.leagueRank[rank];
                }
            }
            line("| " + row.typeName() + " (" + f0(row.strength()) + ") | " + r.strategy + " | "
                    + pct(r.roles.get(MatchRole.STARTER), r.matches) + " | "
                    + pct(r.roles.get(MatchRole.SUB), r.matches) + " | "
                    + pct(r.roles.get(MatchRole.BENCH), r.matches) + " | "
                    + pct(r.roles.get(MatchRole.ABSENT), r.matches) + " | "
                    + roleSplit(r.halfRoles[0]) + " | " + roleSplit(r.halfRoles[1]) + " | "
                    + firstWeek(r.subFirstWeek) + " | "
                    + firstWeek(r.starterFirstWeek) + " | "
                    + (r.appearances == 0 ? "- (출전 없음)" : f2(r.ratingSum / r.appearances)) + " | "
                    + f2(rankSum / r.runs) + " | "
                    + pct(r.leagueRank[1], r.runs) + " | "
                    + pct(top4, r.runs) + " | "
                    + pct(r.leagueRank[8], r.runs) + " |");
        }
        line("");
        return out.toString();
    }

    /** [선발, 교체, 벤치, 결장] → "60/38/0" (경기 수 대비 %) */
    private static String roleSplit(int[] c) {
        if (c == null) {
            return "-";
        }
        int total = java.util.Arrays.stream(c).sum();
        if (total == 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.0f/%.0f/%.0f", 100.0 * c[0] / total, 100.0 * c[1] / total,
                100.0 * c[2] / total);
    }

    private static String halfLabel(com.soccergame.domain.config.ClutchMoments.Half half) {
        return half == com.soccergame.domain.config.ClutchMoments.Half.FIRST ? "전반" : "후반";
    }

    private static String scoreLabel(com.soccergame.domain.config.ClutchMoments.ScoreState state) {
        return switch (state) {
            case LEADING -> "앞섬";
            case TIED -> "동점";
            case TRAILING -> "뒤짐";
        };
    }

    private static String firstWeek(List<Double> weeks) {
        Distribution d = Distribution.of(StrategyReport.arr(weeks));
        long never = weeks.stream().filter(w -> w >= StrategyReport.NEVER).count();
        String median = d.p50() >= StrategyReport.NEVER ? "없음" : f0(d.p50()) + "주";
        return median + " (" + pct((int) never, weeks.size()) + ")";
    }

    private static String f0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private void section(String title) {
        line("## " + title);
        line("");
    }

    private void header(String first, List<StrategyReport> reports) {
        StringBuilder h = new StringBuilder("| " + first + " |");
        StringBuilder s = new StringBuilder("|---|");
        for (StrategyReport r : reports) {
            h.append(' ').append(r.strategy).append(" |");
            s.append("---|");
        }
        line(h.toString());
        line(s.toString());
    }

    private void row(String label, List<StrategyReport> reports, Function<StrategyReport, String> cell) {
        StringBuilder sb = new StringBuilder("| " + label + " |");
        for (StrategyReport r : reports) {
            sb.append(' ').append(cell.apply(r)).append(" |");
        }
        line(sb.toString());
    }

    private void distRow(String label, List<StrategyReport> reports, Function<StrategyReport, List<Double>> values) {
        row(label, reports, r -> {
            Distribution d = Distribution.of(StrategyReport.arr(values.apply(r)));
            return f1(d.mean()) + " (" + f1(d.sd()) + ") / " + f1(d.min()) + " · " + f1(d.p10()) + " · "
                    + f1(d.p50()) + " · " + f1(d.p90()) + " · " + f1(d.max());
        });
    }

    private void line(String s) {
        out.append(s).append('\n');
    }

    private static double div(double a, double b) {
        return b == 0 ? 0 : a / b;
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String pct(int count, int total) {
        return total == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * count / total);
    }
}
