package com.soccergame.simulator;

import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.model.MatchRole;

import java.util.List;
import java.util.Locale;
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
        row("보충수업 발생 비율 (7월 점검)", reports, r -> pct(r.remedialRuns, r.runs));
        row("12월 점검 미달 비율 (영향은 다음 학년)", reports, r -> pct(r.decemberFailRuns, r.runs));
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
        for (String stage : List.of("8강 전 탈락", "8강", "4강", "우승")) {
            row("춘계배 최고 성적: " + stage, reports, r -> pct(r.cupBest.getOrDefault(stage, 0), r.runs));
        }
        line("");

        section("6. 기타");
        header("항목", reports);
        distRow("이벤트 발생 수/년", reports, r -> r.eventsPerRun);
        distRow("1년 뒤 돈(원)", reports, r -> r.money);
        line("");
        line("이벤트별 판당 평균 발생 횟수");
        line("");
        header("이벤트", reports);
        for (var e : config.events().events()) {
            row(e.id() + " " + e.title() + (e.once() ? " (1회)" : ""), reports,
                    r -> f2((double) r.eventCounts.getOrDefault(e.id(), 0) / r.runs));
        }
        line("");
        return out.toString();
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
