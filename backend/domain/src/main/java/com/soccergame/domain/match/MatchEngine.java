package com.soccergame.domain.match;

import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.Rules.MatchRules;
import com.soccergame.domain.config.Scenes.SceneDef;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.Stats;
import com.soccergame.domain.random.Rng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 경기 판정. 상태를 바꾸지 않고 결과만 계산한다(적용은 Competitions 가 한다).
 *
 * 난수 사용 순서: 우리 팀 득점 → 상대 득점 → 우리 득점 시각 → 상대 득점 시각 → 장면 수 → 장면 종류
 * → 장면별 판정(압박 성공 시 득점 판정) → 승부차기 → 패시브 성장 대상.
 */
public final class MatchEngine {
    private final GameConfig config;
    private final MatchRules rules;

    public MatchEngine(GameConfig config) {
        this.config = config;
        this.rules = config.rules().match();
    }

    /** 출전 점수 = 감독 관계도 × w1 + 훈련 능력치 중 상위 N개의 평균 × w2 */
    public double selectionScore(double coachAffinity, Stats stats) {
        Rules.Selection sel = rules.selection();
        double topAverage = config.trainedStatKeys().stream()
                .mapToDouble(stats::get)
                .boxed()
                .sorted(Comparator.reverseOrder())
                .limit(sel.topStatCount())
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        return coachAffinity * sel.coachWeight() + topAverage * sel.statWeight();
    }

    public MatchRole role(double selectionScore, double teamStrength, boolean injured) {
        if (injured) {
            return MatchRole.ABSENT;
        }
        Rules.Selection sel = rules.selection();
        if (selectionScore >= teamStrength + sel.starterMargin()) {
            return MatchRole.STARTER;
        }
        if (selectionScore >= teamStrength + sel.subMargin()) {
            return MatchRole.SUB;
        }
        return MatchRole.BENCH;
    }

    /** 기대 득점 = base + (자기 전력 - 상대 전력) × perStrength, [min, max] 로 제한 */
    public double expectedGoals(double ownStrength, double opponentStrength) {
        Rules.TeamGoals tg = rules.teamGoals();
        return clamp(tg.base() + (ownStrength - opponentStrength) * tg.perStrength(), tg.min(), tg.max());
    }

    /** 플레이어가 없는 경기 (양 팀 포아송) */
    public int[] simulateTeams(Rng rng, double homeStrength, double awayStrength) {
        return new int[]{rng.poisson(expectedGoals(homeStrength, awayStrength)),
                rng.poisson(expectedGoals(awayStrength, homeStrength))};
    }

    /** 패시브 보정 = (값 - pivot) / divisor, [min, max] */
    public double passiveModifier(double value) {
        Rules.PassiveRules p = rules.passive();
        return clamp((value - p.pivot()) / p.divisor(), p.min(), p.max());
    }

    /** 판정 입력값 */
    public record Input(Stats stats, MatchRole role, double conditionModifier, double ownStrength,
                        double opponentStrength, String opponentDefenderPassive, String opponentDefenderName,
                        boolean knockout) {
    }

    /** 플레이어 장면까지 포함한 경기 결과 (적용 전) */
    public record Result(int teamGoals, int opponentGoals, int playerGoals, int playerAssists, int pressGoals,
                         int ourScore, int theirScore, Boolean penaltyWin, List<SceneLog> scenes,
                         List<TimelineEntry> timeline, int successes, int failures) {
        public String outcome() {
            return ourScore > theirScore ? "W" : ourScore < theirScore ? "L" : "D";
        }

        /** 토너먼트 기준 승리 여부 (무승부면 승부차기) */
        public boolean advances() {
            return ourScore > theirScore || (ourScore == theirScore && Boolean.TRUE.equals(penaltyWin));
        }
    }

    public Result play(Rng rng, Input in) {
        int teamGoals = rng.poisson(expectedGoals(in.ownStrength(), in.opponentStrength()));
        int oppGoals = rng.poisson(expectedGoals(in.opponentStrength(), in.ownStrength()));
        List<Integer> ourMinutes = minutes(rng, teamGoals);
        List<Integer> theirMinutes = minutes(rng, oppGoals);

        List<SceneDef> picked = new ArrayList<>();
        if (in.role().plays()) {
            Rules.IntRange count = rules.sceneCount().of(in.role());
            int n = rng.between(count.min(), count.max());
            for (int i = 0; i < n; i++) {
                picked.add(rng.pick(config.scenes().scenes()));
            }
        }

        // 장면 판정
        List<SceneLog> logs = new ArrayList<>();
        List<Integer> extraOurGoals = new ArrayList<>();
        int playerGoals = 0;
        int assists = 0;
        int pressGoals = 0;
        int successes = 0;
        int failures = 0;
        Boolean previous = null;
        boolean takeOnPending = false;
        for (int i = 0; i < picked.size(); i++) {
            int minute = (i + 1) * rules.minutes() / (picked.size() + 1);
            List<SceneDef> queue = new ArrayList<>();
            queue.add(picked.get(i));
            for (int q = 0; q < queue.size(); q++) {
                SceneDef scene = queue.get(q);
                boolean extra = q > 0;
                int our = countUpTo(ourMinutes, minute) + countUpTo(extraOurGoals, minute);
                int their = countUpTo(theirMinutes, minute);

                List<String> mods = new ArrayList<>();
                Rules.Judgement j = rules.judgement();
                double statValue = in.stats().average(scene.stats());
                double p = (scene.isGoalScene() ? j.goalBase() : j.otherBase())
                        + (statValue - in.opponentStrength()) * j.statFactor();
                mods.add("능력치 " + fmt(statValue) + " vs 전력 " + fmt(in.opponentStrength()));
                if (in.conditionModifier() != 0) {
                    p += in.conditionModifier();
                    mods.add("컨디션 " + signed(in.conditionModifier()));
                }
                p += passiveTotal(in, previous, minute, our, their, mods);
                if (scene.isGoalScene() && takeOnPending) {
                    p += rules.takeOnBonus();
                    mods.add("돌파 보너스 " + signed(rules.takeOnBonus()));
                    takeOnPending = false;
                }
                p = clamp(p, j.min(), j.max());

                boolean success = rng.nextDouble() * 100 < p;
                String result;
                String text = success ? scene.success() : scene.failure();
                if (success) {
                    successes++;
                    switch (scene.outcome()) {
                        case GOAL -> {
                            playerGoals++;
                            extraOurGoals.add(minute);
                            result = "GOAL";
                        }
                        case ASSIST -> {
                            assists++;
                            extraOurGoals.add(minute);
                            result = "ASSIST";
                        }
                        case EXTRA_SCENE -> {
                            if (!extra) {
                                queue.add(config.scene(scene.extraScene()));
                            }
                            result = "SUCCESS";
                        }
                        case NEXT_SHOT_BONUS -> {
                            takeOnPending = true;
                            result = "SUCCESS";
                        }
                        case PRESS -> {
                            if (rng.chance(rules.pressingGoalChance())) {
                                pressGoals++;
                                extraOurGoals.add(minute);
                                text += " 뺏은 공이 그대로 팀 득점으로 이어졌다!";
                                result = "PRESS_GOAL";
                            } else {
                                result = "SUCCESS";
                            }
                        }
                        default -> throw new IllegalStateException();
                    }
                } else {
                    failures++;
                    result = "FAIL";
                }
                previous = success;
                logs.add(new SceneLog(minute, scene.id(), scene.name(), extra, round1(p), success, result, text,
                        List.copyOf(mods)));
            }
        }

        int ourScore = teamGoals + playerGoals + assists + pressGoals;
        Boolean penaltyWin = null;
        if (in.knockout() && ourScore == oppGoals) {
            penaltyWin = rng.chance(rules.penaltyWinChance());
        }
        List<TimelineEntry> timeline = timeline(ourMinutes, theirMinutes, logs, penaltyWin);
        return new Result(teamGoals, oppGoals, playerGoals, assists, pressGoals, ourScore, oppGoals, penaltyWin,
                logs, timeline, successes, failures);
    }

    /**
     * 패시브 보정 합계. 상황을 만족한 것만 더하고 합계를 [totalMin, totalMax] 로 제한한다.
     * 기세는 0 미만이면 0.
     */
    private double passiveTotal(Input in, Boolean previous, int minute, int our, int their, List<String> mods) {
        Rules.PassiveRules pr = rules.passive();
        double total = 0;
        if (Boolean.TRUE.equals(previous)) {
            double m = Math.max(0, passiveModifier(in.stats().get(GameConfig.MOMENTUM)));
            total += m;
            mods.add("기세 " + signed(m));
        }
        if (minute >= pr.clutchFromMinute() && Math.abs(our - their) <= pr.clutchMaxGoalDiff()) {
            double m = passiveModifier(in.stats().get(GameConfig.CLUTCH));
            total += m;
            mods.add("클러치 " + signed(m));
        }
        if (Boolean.FALSE.equals(previous) || our < their) {
            double m = passiveModifier(in.stats().get(GameConfig.MENTAL));
            total += m;
            mods.add("멘탈 " + signed(m));
        }
        double typeMod = passiveModifier(in.stats().get(in.opponentDefenderPassive()));
        total += typeMod;
        mods.add(in.opponentDefenderName() + " 대응 " + signed(typeMod));
        return clamp(total, pr.totalMin(), pr.totalMax());
    }

    /** 출전 경기 평점 */
    public double rating(Result r) {
        Rules.RatingRules rr = rules.rating();
        int other = r.successes() - r.playerGoals() - r.playerAssists();
        double v = rr.base() + r.playerGoals() * rr.goal() + r.playerAssists() * rr.assist()
                + other * rr.otherSuccess() + r.failures() * rr.failure();
        return round1(clamp(v, rr.min(), rr.max()));
    }

    private List<Integer> minutes(Rng rng, int goals) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < goals; i++) {
            list.add(rng.between(1, rules.minutes()));
        }
        list.sort(Comparator.naturalOrder());
        return list;
    }

    private static int countUpTo(List<Integer> minutes, int minute) {
        int c = 0;
        for (int m : minutes) {
            if (m <= minute) {
                c++;
            }
        }
        return c;
    }

    /** 득점과 장면을 시간순으로 엮는다. 같은 분이면 팀 득점이 장면보다 먼저다. */
    private List<TimelineEntry> timeline(List<Integer> ourMinutes, List<Integer> theirMinutes, List<SceneLog> scenes,
                                         Boolean penaltyWin) {
        record Item(int minute, int order, int seq, String kind, String text, int ourDelta, int theirDelta) {
        }
        List<Item> items = new ArrayList<>();
        int seq = 0;
        for (int m : ourMinutes) {
            items.add(new Item(m, 0, seq++, "TEAM_GOAL", "동료의 득점! 팀이 한 골을 넣었다.", 1, 0));
        }
        for (int m : theirMinutes) {
            items.add(new Item(m, 0, seq++, "OPPONENT_GOAL", "실점. 상대에게 골을 내줬다.", 0, 1));
        }
        for (SceneLog s : scenes) {
            int delta = switch (s.result()) {
                case "GOAL", "ASSIST", "PRESS_GOAL" -> 1;
                default -> 0;
            };
            String text = "[" + s.sceneName() + " · 성공률 " + Math.round(s.probability()) + "%] " + s.text();
            items.add(new Item(s.minute(), 1, seq++, s.success() ? "SCENE_SUCCESS" : "SCENE_FAIL", text, delta, 0));
        }
        items.sort(Comparator.comparingInt(Item::minute).thenComparingInt(Item::order).thenComparingInt(Item::seq));
        List<TimelineEntry> out = new ArrayList<>();
        int our = 0;
        int their = 0;
        out.add(new TimelineEntry(0, "KICKOFF", "킥오프!", 0, 0));
        boolean halfTimeShown = false;
        int half = rules.minutes() / 2;
        for (Item it : items) {
            if (!halfTimeShown && it.minute() > half) {
                out.add(new TimelineEntry(half, "HALF_TIME", "전반 종료.", our, their));
                halfTimeShown = true;
            }
            our += it.ourDelta();
            their += it.theirDelta();
            out.add(new TimelineEntry(it.minute(), it.kind(), it.text(), our, their));
        }
        if (!halfTimeShown) {
            out.add(new TimelineEntry(half, "HALF_TIME", "전반 종료.", our, their));
        }
        out.add(new TimelineEntry(rules.minutes(), "FULL_TIME", "경기 종료.", our, their));
        if (penaltyWin != null) {
            out.add(new TimelineEntry(rules.minutes(), "PENALTIES",
                    penaltyWin ? "승부차기 끝에 승리했다!" : "승부차기 끝에 졌다.", our, their));
        }
        return out;
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static String signed(double v) {
        return (v >= 0 ? "+" : "") + String.format("%.1f", v);
    }
}
