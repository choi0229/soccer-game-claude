package com.soccergame.simulator;

import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.engine.Modifiers;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.model.MatchRole;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 한 전략의 N회 결과를 모은다. */
public final class StrategyReport {
    public final String strategy;
    private final GameConfig config;
    private final int weeks;

    final List<Double> primaryAvg = new ArrayList<>();
    final Map<String, List<Double>> finalStats = new LinkedHashMap<>();
    final List<Double> trainedAvg = new ArrayList<>();
    final List<Double> trainedGain = new ArrayList<>();
    final List<Double> primaryGain = new ArrayList<>();
    final List<Double> passiveAvg = new ArrayList<>();
    final double[] weeklyStamina;
    final List<Double> exclusions = new ArrayList<>();
    final List<Double> injuries = new ArrayList<>();
    final List<Double> academics = new ArrayList<>();
    final List<Double> makeupWeeks = new ArrayList<>();
    final List<Double> makeupDays = new ArrayList<>();
    int makeupRuns;
    final Map<MatchRole, Integer> roles = new EnumMap<>(MatchRole.class);
    int matches;
    int appearances;
    long scenes;
    long goals;
    long assists;
    long pressGoals;
    double ratingSum;
    long teamPoissonGoals;
    long ourScore;
    long theirScore;
    final List<Double> seasonGoals = new ArrayList<>();
    final List<Double> seasonAssists = new ArrayList<>();
    final List<Double> reputation = new ArrayList<>();
    final int[] leagueRank = new int[9];
    final Map<String, Integer> cupBest = new LinkedHashMap<>();
    final List<Double> starterFirstWeek = new ArrayList<>();
    final List<Double> subFirstWeek = new ArrayList<>();
    final List<Double> coachAffinity = new ArrayList<>();
    final List<Double> money = new ArrayList<>();
    final List<Double> eventsPerRun = new ArrayList<>();
    final Map<String, Integer> eventCounts = new LinkedHashMap<>();
    /** 특성 키 → 단계별 판 수 [0..3], 점수 합 */
    final Map<String, int[]> traitTiers = new LinkedHashMap<>();
    final Map<String, Long> traitScoreSum = new LinkedHashMap<>();
    /** 축 → [잠김, 0, 1, 2, 3] 판 수 */
    final Map<Axis, int[]> bondTiers = new LinkedHashMap<>();
    final List<Double> girlfriendUnlockWeek = new ArrayList<>();
    long menuTrainings;
    double bonusApplied;
    long bonusCapped;
    int runs;

    private final Modifiers modifiers;

    public StrategyReport(String strategy, GameConfig config, int weeks) {
        this.strategy = strategy;
        this.config = config;
        this.modifiers = new Modifiers(config);
        this.weeks = weeks;
        this.weeklyStamina = new double[weeks];
        config.allStatKeys().forEach(k -> finalStats.put(k, new ArrayList<>()));
        for (MatchRole r : MatchRole.values()) {
            roles.put(r, 0);
        }
    }

    public void add(GameState s) {
        runs++;
        List<String> primary = config.rules().archetype().primaryStats();
        List<String> trained = config.trainedStatKeys();
        primaryAvg.add(s.stats.average(primary));
        primaryGain.add(s.stats.average(primary) - s.initialStats.average(primary));
        trainedAvg.add(s.stats.average(trained));
        trainedGain.add(s.stats.average(trained) - s.initialStats.average(trained));
        passiveAvg.add(s.stats.average(config.passiveStatKeys()));
        config.allStatKeys().forEach(k -> finalStats.get(k).add(s.stats.get(k)));
        for (int w = 0; w < weeks; w++) {
            weeklyStamina[w] += s.metrics.averageStamina(w);
        }
        exclusions.add((double) s.metrics.exclusions);
        injuries.add((double) s.metrics.injuries);
        academics.add(s.academics);
        makeupWeeks.add((double) s.makeupWeeks.size());
        makeupDays.add((double) s.metrics.makeupDays);
        if (!s.makeupWeeks.isEmpty()) {
            makeupRuns++;
        }
        int g = 0;
        int a = 0;
        int firstStarter = -1;
        int firstSub = -1;
        for (MatchRecord m : s.matches) {
            matches++;
            roles.merge(m.role(), 1, Integer::sum);
            teamPoissonGoals += m.teamGoals();
            ourScore += m.ourScore();
            theirScore += m.theirScore();
            if (m.role().plays()) {
                appearances++;
                scenes += m.scenes().size();
                ratingSum += m.rating();
            }
            if (m.role() == MatchRole.STARTER && firstStarter < 0) {
                firstStarter = m.week() + 1;
            }
            if (m.role() == MatchRole.SUB && firstSub < 0) {
                firstSub = m.week() + 1;
            }
            g += m.playerGoals();
            a += m.playerAssists();
            pressGoals += m.pressGoals();
        }
        goals += g;
        assists += a;
        seasonGoals.add((double) g);
        seasonAssists.add((double) a);
        starterFirstWeek.add((double) (firstStarter < 0 ? NEVER : firstStarter));
        subFirstWeek.add((double) (firstSub < 0 ? NEVER : firstSub));
        reputation.add(s.reputation);
        leagueRank[s.league.rankOf(s.playerSchoolId)]++;
        String best = s.cupStagesReached.isEmpty() ? "8강 전 탈락" : s.cupStagesReached.getLast();
        if (s.cupStagesReached.isEmpty() && s.cup.isAlive(s.playerSchoolId)) {
            best = "우승";
        }
        cupBest.merge(best, 1, Integer::sum);
        coachAffinity.add(s.affinity.get(Axis.COACH));
        money.add((double) s.money);
        eventsPerRun.add((double) s.eventHistory.size());
        for (var trait : config.rules().traits().list()) {
            int score = s.traitScores.getOrDefault(trait.key(), 0);
            traitTiers.computeIfAbsent(trait.key(), k -> new int[config.rules().traits().tiers().size() + 1])
                    [modifiers.traitTier(s, trait.key())]++;
            traitScoreSum.merge(trait.key(), (long) score, Long::sum);
        }
        for (var bond : config.rules().bonds().axes()) {
            int tier = modifiers.bondTier(s, bond.axis());
            bondTiers.computeIfAbsent(bond.axis(), k -> new int[config.rules().bonds().tiers().size() + 2])[tier + 1]++;
        }
        Integer unlocked = s.axisUnlockedWeek.get(Axis.GIRLFRIEND);
        girlfriendUnlockWeek.add((double) (unlocked == null ? NEVER : unlocked + 1));
        menuTrainings += s.metrics.menuTrainings;
        bonusApplied += s.metrics.bonusApplied;
        bonusCapped += s.metrics.bonusCapped;
        s.eventHistory.forEach(id -> eventCounts.merge(id, 1, Integer::sum));
    }

    /** 1년 동안 한 번도 없었음을 나타내는 주차 */
    static final int NEVER = 99;

    static double[] arr(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
