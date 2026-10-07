package com.soccergame.domain.match;

import com.soccergame.domain.config.ClutchMoments;
import com.soccergame.domain.config.ClutchMoments.MomentChoice;
import com.soccergame.domain.config.ClutchMoments.MomentDef;
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
 * 경기 판정. 게임 상태를 바꾸지 않고 결과만 계산한다(적용은 Competitions 가 한다).
 *
 * 난수 사용 순서: 우리 팀 득점 → 상대 득점 → 우리 득점 시각 → 상대 득점 시각 → 장면 수 → 장면 종류
 * → 승부처 여부 → (승부처면) 대체할 장면 순번 → 장면별 판정(압박 성공 시 득점 판정) → [승부처 종류 → 선택 판정]
 * → 남은 장면 → 승부차기.
 * 승부처가 나오면 그 직전에서 멈추고(start 가 반환), 선택을 받으면 resolveClutch 로 이어서 계산한다.
 */
public final class MatchEngine {
    private final GameConfig config;
    private final MatchRules rules;
    private final ClutchMoments clutch;

    public MatchEngine(GameConfig config) {
        this.config = config;
        this.rules = config.rules().match();
        this.clutch = config.clutchMoments();
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
        if (selectionScore >= teamStrength * sel.starterRatio()) {
            return MatchRole.STARTER;
        }
        if (selectionScore >= teamStrength * sel.subRatio()) {
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

    /** 판정 입력값. allowClutch 가 false 면 승부처가 나오지 않는다. */
    public record Input(Stats stats, MatchRole role, double conditionModifier, double ownStrength,
                        double opponentStrength, String opponentDefenderPassive, String opponentDefenderName,
                        boolean knockout, boolean allowClutch) {
    }

    /** 플레이어 장면까지 포함한 경기 결과 (적용 전) */
    public record Result(int teamGoals, int opponentGoals, int playerGoals, int playerAssists, int pressGoals,
                         int clutchTeamGoals, int ourScore, int theirScore, Boolean penaltyWin, List<SceneLog> scenes,
                         List<TimelineEntry> timeline, int successes, int failures, int goalFailures,
                         double ratingDelta, ClutchLog clutch) {
        public String outcome() {
            return ourScore > theirScore ? "W" : ourScore < theirScore ? "L" : "D";
        }

        /** 토너먼트 기준 승리 여부 (무승부면 승부차기) */
        public boolean advances() {
            return ourScore > theirScore || (ourScore == theirScore && Boolean.TRUE.equals(penaltyWin));
        }
    }

    /** 승부처가 없는 경기를 끝까지 계산한다 (승부처에서 멈추면 예외). */
    public Result play(Rng rng, Input in) {
        MatchSession s = start(rng, in);
        if (!s.finished()) {
            throw new IllegalStateException("승부처 선택이 필요한 경기입니다");
        }
        return s.result();
    }

    /** 경기를 시작해 승부처 직전까지(없으면 끝까지) 계산한다. */
    public MatchSession start(Rng rng, Input in) {
        int teamGoals = rng.poisson(expectedGoals(in.ownStrength(), in.opponentStrength()));
        int oppGoals = rng.poisson(expectedGoals(in.opponentStrength(), in.ownStrength()));
        List<Integer> ourMinutes = minutes(rng, teamGoals);
        List<Integer> theirMinutes = minutes(rng, oppGoals);

        List<SceneDef> picked = new ArrayList<>();
        int clutchSlot = -1;
        if (in.role().plays()) {
            Rules.IntRange count = rules.sceneCount().of(in.role());
            int n = rng.between(count.min(), count.max());
            for (int i = 0; i < n; i++) {
                picked.add(rng.pick(config.scenes().scenes()));
            }
            if (in.allowClutch() && n > 0) {
                double chance = in.role() == MatchRole.STARTER ? clutch.chance().starter() : clutch.chance().sub();
                if (rng.chance(chance)) {
                    clutchSlot = rng.nextInt(n);
                }
            }
        }
        MatchSession s = new MatchSession(in, teamGoals, oppGoals, ourMinutes, theirMinutes, picked, clutchSlot);
        advance(rng, s);
        return s;
    }

    /** 장면을 차례로 판정한다. 승부처 차례가 오면 멈춘다. */
    private void advance(Rng rng, MatchSession s) {
        while (s.index < s.picked.size()) {
            int minute = minuteOf(s, s.index);
            if (s.index == s.clutchSlot) {
                int our = ourScoreAt(s, minute);
                int their = countUpTo(s.theirMinutes, minute);
                ClutchMoments.Half half = minute <= rules.minutes() / 2 ? ClutchMoments.Half.FIRST
                        : ClutchMoments.Half.SECOND;
                ClutchMoments.ScoreState state = our > their ? ClutchMoments.ScoreState.LEADING
                        : our < their ? ClutchMoments.ScoreState.TRAILING : ClutchMoments.ScoreState.TIED;
                List<MomentDef> candidates = clutch.moments().stream()
                        .filter(m -> m.condition().half() == half && m.condition().score() == state).toList();
                if (!candidates.isEmpty()) {
                    s.moment = rng.pick(candidates);
                    s.momentMinute = minute;
                    return;
                }
            }
            judgeScene(rng, s, s.picked.get(s.index), minute, false);
            s.index++;
        }
        finish(rng, s);
    }

    /** 지금 승부처의 선택지와 성공 확률 (난수를 쓰지 않는다) */
    public List<ClutchOption> clutchOptions(MatchSession s) {
        if (!s.awaitingChoice()) {
            return List.of();
        }
        List<ClutchOption> options = new ArrayList<>();
        int our = ourScoreAt(s, s.momentMinute);
        int their = countUpTo(s.theirMinutes, s.momentMinute);
        for (int i = 0; i < s.moment.choices().size(); i++) {
            MomentChoice c = s.moment.choices().get(i);
            List<String> mods = new ArrayList<>();
            double p = probability(s, c.stats(), c.base(), c.success().outcome() == ClutchMoments.Outcome.GOAL,
                    s.momentMinute, our, their, mods);
            options.add(new ClutchOption(i, c.text(), c.style(),
                    c.stats().stream().map(k -> config.stat(k).name()).toList(), round1(p), List.copyOf(mods),
                    rewardText(c), c.failRating(), c.trait()));
        }
        return options;
    }

    /** 승부처 선택을 판정하고 경기를 끝까지 계산한다. */
    public void resolveClutch(Rng rng, MatchSession s, int choiceIndex) {
        if (!s.awaitingChoice()) {
            throw new IllegalStateException("선택을 기다리는 승부처가 없습니다");
        }
        MomentDef moment = s.moment;
        MomentChoice c = moment.choices().get(choiceIndex);
        int minute = s.momentMinute;
        int our = ourScoreAt(s, minute);
        int their = countUpTo(s.theirMinutes, minute);
        boolean goalJudgement = c.success().outcome() == ClutchMoments.Outcome.GOAL;
        List<String> mods = new ArrayList<>();
        double p = probability(s, c.stats(), c.base(), goalJudgement, minute, our, their, mods);
        if (goalJudgement && s.takeOnPending) {
            s.takeOnPending = false;
        }
        boolean success = rng.nextDouble() * 100 < p;
        String result;
        String text = c.text() + " → ";
        SceneDef extra = null;
        Rules.RatingRules rr = rules.rating();
        double ratingChange;
        if (success) {
            s.successes++;
            ratingChange = switch (c.success().outcome()) {
                case GOAL -> rr.goal();
                case ASSIST -> rr.assist();
                case EXTRA_SHOT, TEAM_GOAL_CHANCE -> rr.otherSuccess();
            };
            switch (c.success().outcome()) {
                case GOAL -> {
                    s.playerGoals++;
                    s.extraOurGoals.add(minute);
                    result = "GOAL";
                    text += "성공, 골!";
                }
                case ASSIST -> {
                    s.assists++;
                    s.extraOurGoals.add(minute);
                    result = "ASSIST";
                    text += "성공, 동료의 골을 도왔다! 도움!";
                }
                case EXTRA_SHOT -> {
                    extra = config.scene(c.success().extraScene());
                    result = "SUCCESS";
                    text += "성공, " + extra.name() + " 기회가 이어진다!";
                }
                case TEAM_GOAL_CHANCE -> {
                    if (rng.chance(c.success().teamGoalChance())) {
                        s.clutchTeamGoals++;
                        s.extraOurGoals.add(minute);
                        result = "TEAM_GOAL";
                        text += "성공, 흐름이 이어져 팀이 골을 넣었다!";
                    } else {
                        result = "SUCCESS";
                        text += "성공, 공을 지켜 냈다.";
                    }
                }
                default -> throw new IllegalStateException();
            }
        } else {
            s.ratingDelta += c.failRating();
            ratingChange = c.failRating();
            result = "FAIL";
            text += "실패.";
        }
        s.previous = success;
        s.logs.add(new SceneLog(minute, "CLUTCH", "승부처: " + moment.title(), false, round1(p), success, result,
                text, List.copyOf(mods)));
        s.clutchLog = new ClutchLog(moment.id(), moment.title(), moment.situation(), minute, choiceIndex, c.text(),
                c.style(), round1(p), success, result, ratingChange);
        s.moment = null;
        if (extra != null) {
            judgeSingle(rng, s, extra, minute, true);
        }
        s.index++;
        advance(rng, s);
    }

    /** 기본 장면 하나 (뒷공간 침투 성공 시 추가 장면 포함) */
    private void judgeScene(Rng rng, MatchSession s, SceneDef scene, int minute, boolean extra) {
        SceneDef next = judgeSingle(rng, s, scene, minute, extra);
        if (next != null) {
            judgeSingle(rng, s, next, minute, true);
        }
    }

    /** 장면 하나를 판정한다. 추가 장면이 생기면 돌려준다(추가 장면은 다시 추가하지 않는다). */
    private SceneDef judgeSingle(Rng rng, MatchSession s, SceneDef scene, int minute, boolean extra) {
        int our = ourScoreAt(s, minute);
        int their = countUpTo(s.theirMinutes, minute);
        Rules.Judgement j = rules.judgement();
        List<String> mods = new ArrayList<>();
        double base = scene.isGoalScene() ? j.goalBase() : j.otherBase();
        double p = probability(s, scene.stats(), base, scene.isGoalScene(), minute, our, their, mods);
        if (scene.isGoalScene() && s.takeOnPending) {
            s.takeOnPending = false;
        }
        boolean success = rng.nextDouble() * 100 < p;
        String result;
        String text = success ? scene.success() : scene.failure();
        SceneDef next = null;
        if (success) {
            s.successes++;
            switch (scene.outcome()) {
                case GOAL -> {
                    s.playerGoals++;
                    s.extraOurGoals.add(minute);
                    result = "GOAL";
                }
                case ASSIST -> {
                    s.assists++;
                    s.extraOurGoals.add(minute);
                    result = "ASSIST";
                }
                case EXTRA_SCENE -> {
                    if (!extra) {
                        next = config.scene(scene.extraScene());
                    }
                    result = "SUCCESS";
                }
                case NEXT_SHOT_BONUS -> {
                    s.takeOnPending = true;
                    result = "SUCCESS";
                }
                case PRESS -> {
                    if (rng.chance(rules.pressingGoalChance())) {
                        s.pressGoals++;
                        s.extraOurGoals.add(minute);
                        text += " 뺏은 공이 그대로 팀 득점으로 이어졌다!";
                        result = "PRESS_GOAL";
                    } else {
                        result = "SUCCESS";
                    }
                }
                default -> throw new IllegalStateException();
            }
        } else {
            s.failures++;
            if (scene.isGoalScene()) {
                s.goalFailures++;
            }
            result = "FAIL";
        }
        s.previous = success;
        s.logs.add(new SceneLog(minute, scene.id(), scene.name(), extra, round1(p), success, result, text,
                List.copyOf(mods)));
        return next;
    }

    /**
     * 성공 확률(%) = 기준값 + (능력치 - 상대 전력) × statFactor + 컨디션 + 패시브 보정 (+ 돌파 보너스), [min, max].
     * 난수를 쓰지 않고 상태도 바꾸지 않는다.
     */
    private double probability(MatchSession s, List<String> stats, double base, boolean goalJudgement, int minute,
                               int our, int their, List<String> mods) {
        Input in = s.input;
        Rules.Judgement j = rules.judgement();
        double statValue = in.stats().average(stats);
        double p = base + (statValue - in.opponentStrength()) * j.statFactor();
        mods.add("기준 " + fmt(base) + ", 능력치 " + fmt(statValue) + " vs 전력 " + fmt(in.opponentStrength()));
        if (in.conditionModifier() != 0) {
            p += in.conditionModifier();
            mods.add("컨디션 " + signed(in.conditionModifier()));
        }
        p += passiveTotal(in, s.previous, minute, our, their, mods);
        if (goalJudgement && s.takeOnPending) {
            p += rules.takeOnBonus();
            mods.add("돌파 보너스 " + signed(rules.takeOnBonus()));
        }
        return clamp(p, j.min(), j.max());
    }

    private void finish(Rng rng, MatchSession s) {
        int ourScore = s.teamGoals + s.playerGoals + s.assists + s.pressGoals + s.clutchTeamGoals;
        Boolean penaltyWin = null;
        if (s.input.knockout() && ourScore == s.opponentGoals) {
            penaltyWin = rng.chance(rules.penaltyWinChance());
        }
        List<TimelineEntry> timeline = timeline(s.ourMinutes, s.theirMinutes, s.logs, penaltyWin, null);
        s.result = new Result(s.teamGoals, s.opponentGoals, s.playerGoals, s.assists, s.pressGoals, s.clutchTeamGoals,
                ourScore, s.opponentGoals, penaltyWin, List.copyOf(s.logs), timeline, s.successes, s.failures,
                s.goalFailures, s.ratingDelta, s.clutchLog);
    }

    /** 승부처에서 멈춘 경기의 지금까지 중계 (승부처 이후 정보는 담지 않는다) */
    public List<TimelineEntry> partialTimeline(MatchSession s) {
        int upTo = s.momentMinute;
        List<Integer> ours = s.ourMinutes.stream().filter(m -> m <= upTo).toList();
        List<Integer> theirs = s.theirMinutes.stream().filter(m -> m <= upTo).toList();
        return timeline(ours, theirs, s.logs, null, upTo);
    }

    /**
     * 출전 경기 평점. 기존 장면 실패는 골 장면 -0.1 / 그 밖 -0.2, 승부처 실패는 그 선택지의 감점(ratingDelta).
     */
    public double rating(Result r) {
        Rules.RatingRules rr = rules.rating();
        int other = r.successes() - r.playerGoals() - r.playerAssists();
        double v = rr.base() + r.playerGoals() * rr.goal() + r.playerAssists() * rr.assist()
                + other * rr.otherSuccess() + r.goalFailures() * rr.goalFailure()
                + (r.failures() - r.goalFailures()) * rr.failure() + r.ratingDelta();
        return round1(clamp(v, rr.min(), rr.max()));
    }

    private int minuteOf(MatchSession s, int index) {
        return (index + 1) * rules.minutes() / (s.picked.size() + 1);
    }

    private static int ourScoreAt(MatchSession s, int minute) {
        return countUpTo(s.ourMinutes, minute) + countUpTo(s.extraOurGoals, minute);
    }

    private String rewardText(MomentChoice c) {
        return switch (c.success().outcome()) {
            case GOAL -> "성공하면 골";
            case ASSIST -> "성공하면 도움";
            case EXTRA_SHOT -> "성공하면 " + config.scene(c.success().extraScene()).name() + " 장면 추가";
            case TEAM_GOAL_CHANCE -> "성공하면 " + Math.round(c.success().teamGoalChance() * 100) + "% 확률로 팀 득점";
        };
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

    /**
     * 득점과 장면을 시간순으로 엮는다. 같은 분이면 팀 득점이 장면보다 먼저다.
     * upTo 가 있으면 그 분까지의 중계만 만든다(하프타임은 지난 경우에만, 경기 종료 없음).
     */
    private List<TimelineEntry> timeline(List<Integer> ourMinutes, List<Integer> theirMinutes, List<SceneLog> scenes,
                                         Boolean penaltyWin, Integer upTo) {
        record Item(int minute, int order, int seq, String kind, String text, int ourDelta, int theirDelta,
                    Double probability, List<String> modifiers) {
        }
        List<Item> items = new ArrayList<>();
        int seq = 0;
        for (int m : ourMinutes) {
            items.add(new Item(m, 0, seq++, "TEAM_GOAL", "동료의 득점! 팀이 한 골을 넣었다.", 1, 0, null, null));
        }
        for (int m : theirMinutes) {
            items.add(new Item(m, 0, seq++, "OPPONENT_GOAL", "실점. 상대에게 골을 내줬다.", 0, 1, null, null));
        }
        for (SceneLog s : scenes) {
            int delta = switch (s.result()) {
                case "GOAL", "ASSIST", "PRESS_GOAL", "TEAM_GOAL" -> 1;
                default -> 0;
            };
            boolean isClutch = s.sceneId().equals("CLUTCH");
            String kind = isClutch ? "CLUTCH" : s.success() ? "SCENE_SUCCESS" : "SCENE_FAIL";
            String text = "[" + s.sceneName() + (s.extra() ? " (추가)" : "") + "] " + s.text();
            items.add(new Item(s.minute(), 1, seq++, kind, text, delta, 0, s.probability(), s.modifiers()));
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
            out.add(new TimelineEntry(it.minute(), it.kind(), it.text(), our, their, it.probability(),
                    it.modifiers()));
        }
        if (upTo != null) {
            // 승부처가 후반이면 최종 중계처럼 승부처 앞에 전반 종료가 와야 한다
            if (!halfTimeShown && upTo > half) {
                out.add(new TimelineEntry(half, "HALF_TIME", "전반 종료.", our, their));
            }
            return out;
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
