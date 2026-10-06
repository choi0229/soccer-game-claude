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
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--runs" -> runs = Integer.parseInt(args[++i]);
                case "--seed" -> seedStart = Long.parseLong(args[++i]);
                case "--config" -> configDir = Path.of(args[++i]);
                case "--out" -> outFile = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("알 수 없는 인자: " + args[i]);
            }
        }
        GameConfig config = ConfigLoader.load(configDir);
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

    /** 학교 유형 5종 × (훈련 위주, 균형) */
    static String bySchoolType(GameConfig config, int runs, long seedStart) {
        long started = System.nanoTime();
        List<Strategy> strategies = List.of(new Strategies.TrainingFocus(), new Strategies.Balanced());
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
