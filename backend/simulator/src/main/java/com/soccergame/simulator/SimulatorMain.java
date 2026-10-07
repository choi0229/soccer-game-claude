package com.soccergame.simulator;

import com.soccergame.domain.config.ConfigLoader;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.GameEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용: java -jar simulator.jar [--runs 1000] [--seed 1] [--config ../config] [--out report.md]
 */
public final class SimulatorMain {
    private SimulatorMain() {
    }

    public static void main(String[] args) throws IOException {
        int runs = 1000;
        long seedStart = 1;
        Path configDir = Path.of(System.getenv().getOrDefault("GAME_CONFIG_DIR", "config"));
        Path outFile = null;
        Path seasonLogDir = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--runs" -> runs = Integer.parseInt(args[++i]);
                case "--seed" -> seedStart = Long.parseLong(args[++i]);
                case "--config" -> configDir = Path.of(args[++i]);
                case "--out" -> outFile = Path.of(args[++i]);
                case "--season-log" -> seasonLogDir = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("알 수 없는 인자: " + args[i]);
            }
        }
        GameConfig config = ConfigLoader.load(configDir);
        if (seasonLogDir != null) {
            seasonLogs(config, seedStart, seasonLogDir);
            return;
        }
        String report = run(config, runs, seedStart);
        System.out.println(report);
        if (outFile != null) {
            Files.writeString(outFile, report);
            System.err.println("결과를 저장했습니다: " + outFile.toAbsolutePath());
        }
    }

    public static String run(GameConfig config, int runs, long seedStart) {
        GameEngine engine = new GameEngine(config);
        YearRunner runner = new YearRunner(engine);
        List<StrategyReport> reports = new ArrayList<>();
        long started = System.nanoTime();
        for (Strategy strategy : Strategies.all()) {
            StrategyReport report = new StrategyReport(strategy.name(), config,
                    config.rules().calendar().weeksPerYear());
            for (long seed = seedStart; seed < seedStart + runs; seed++) {
                report.add(runner.run(seed, strategy));
            }
            reports.add(report);
        }
        System.err.printf("%d판 × %d전략 완료 (%.1f초)%n", runs, reports.size(), (System.nanoTime() - started) / 1e9);
        String main = new ReportPrinter(config).render(reports, runs, seedStart);
        return main + bySchoolType(config, runs, seedStart);
    }

    /** 한 판 시즌 기록 문서: (다크호스, 균형), (다크호스, 훈련 위주), (강호, 균형). 같은 시드 */
    static void seasonLogs(GameConfig config, long seed, Path dir) throws IOException {
        Files.createDirectories(dir);
        record Target(String schoolType, Strategy strategy) {
        }
        List<Target> targets = List.of(new Target("DARK_HORSE", new Strategies.Balanced()),
                new Target("DARK_HORSE", new Strategies.TrainingFocus()),
                new Target("STRONG", new Strategies.Balanced()));
        StringBuilder compare = new StringBuilder();
        List<java.util.Map<String, String>> columns = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Target t : targets) {
            SeasonLog log = new SeasonLog(config.withPlayerSchoolType(t.schoolType()));
            SeasonLog.Run run = log.play(seed, t.strategy());
            String name = run.schoolType + "-" + run.strategy.replace(" ", "");
            Files.writeString(dir.resolve(name + ".md"), log.render(run));
            System.err.println("저장: " + dir.resolve(name + ".md"));
            columns.add(log.summary(run));
            names.add(run.schoolType + " + " + run.strategy);
        }
        compare.append("| 항목 | ").append(String.join(" | ", names)).append(" |\n|---|");
        names.forEach(n -> compare.append("---|"));
        compare.append('\n');
        for (String key : columns.getFirst().keySet()) {
            compare.append("| ").append(key);
            columns.forEach(c -> compare.append(" | ").append(c.getOrDefault(key, "-")));
            compare.append(" |\n");
        }
        System.out.println(compare);
    }

    /** 학교 유형 5종 × (훈련 위주, 균형) */
    static String bySchoolType(GameConfig config, int runs, long seedStart) {
        long started = System.nanoTime();
        List<Strategy> strategies = List.of(new Strategies.TrainingFocus(), new Strategies.Balanced(),
                new Strategies.AcademicNeglect());
        List<ReportPrinter.SchoolTypeRow> rows = new ArrayList<>();
        for (var type : config.schools().types()) {
            GameConfig typed = config.withPlayerSchoolType(type.key());
            YearRunner runner = new YearRunner(new GameEngine(typed));
            for (Strategy strategy : strategies) {
                StrategyReport report = new StrategyReport(strategy.name(), typed,
                        typed.rules().calendar().weeksPerYear());
                for (long seed = seedStart; seed < seedStart + runs; seed++) {
                    report.add(runner.run(seed, strategy));
                }
                rows.add(new ReportPrinter.SchoolTypeRow(type.name(), type.strength(), report));
            }
        }
        System.err.printf("학교 유형별 %d판 × %d조합 완료 (%.1f초)%n", runs, rows.size(),
                (System.nanoTime() - started) / 1e9);
        return new ReportPrinter(config).renderSchoolTypes(rows, runs);
    }
}
