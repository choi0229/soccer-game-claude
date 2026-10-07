package com.soccergame.domain.config;

import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.TrainingSlot;

import java.util.List;
import java.util.Map;

/** config/game.yaml 의 구조. */
public record Rules(
        CalendarRules calendar,
        StatsRules stats,
        ArchetypeRules archetype,
        TrainingRules training,
        DailyRules daily,
        StaminaRules stamina,
        ConditionRules condition,
        AcademicsRules academics,
        RelationshipRules relationships,
        ResourceRules resources,
        MatchRules match,
        ReputationRules reputation,
        EventRules events,
        TraitRules traits,
        BondRules bonds) {

    public record WeekRef(int month, int week) {
    }

    public record IntRange(int min, int max) {
    }

    // ---- 캘린더 ----

    public record CalendarRules(int weeksPerYear, int weeksPerMonth, int startMonth,
                                List<Vacation> vacations, LeagueSchedule league, List<TournamentDef> tournaments) {
    }

    public record Vacation(String name, WeekRef from, WeekRef to) {
    }

    public record LeagueSchedule(Weekday matchDay, List<LeagueBlock> blocks) {
    }

    public record LeagueBlock(int fromRound, int toRound, WeekRef from, WeekRef to) {
    }

    /** 단판 토너먼트 하나. 참가 학교 수 = 2^rounds, matchDays 는 라운드 순서대로의 경기일 */
    public record TournamentDef(String key, String name, Period period, Entry entry, int rounds,
                                List<CupDay> matchDays) {
        public int teams() {
            return 1 << rounds;
        }
    }

    public record Period(WeekRef from, WeekRef to) {
    }

    public enum EntryRule {
        /** 전체 학교 */
        ALL,
        /** 권역 리그 최종 순위 상위 topPerRegion 개교씩 */
        LEAGUE_TOP
    }

    public record Entry(EntryRule rule, Integer topPerRegion) {
    }

    public record CupDay(int month, int week, Weekday day) {
    }

    // ---- 능력치 ----

    public record StatsRules(double min, double max, List<StatDef> trained, List<StatDef> passive,
                             StartRanges start, List<GradeDef> grades) {
    }

    public record StatDef(String key, String name, String group) {
    }

    public record StartRanges(IntRange trained, IntRange passive) {
    }

    public record GradeDef(String grade, double min) {
    }

    public record ArchetypeRules(String key, String name, String position, List<String> primaryStats,
                                 double primaryStartBonus, double primaryGrowthMultiplier) {
    }

    // ---- 훈련과 일과 ----

    public record TrainingRules(SlotTraining team, SlotTraining personal, List<DecayStep> decay, double bonusCap) {
    }

    public record SlotTraining(double baseGrowth, double stamina) {
    }

    public record DecayStep(double below, double factor) {
    }

    public record DailyRules(DawnChoice defaultDawn, ClassAttitude defaultClassAttitude, Dawn dawn, Map<ClassAttitude, ClassAttitudeRule> classAttitudes,
                             FriendMultiplier friendMultiplier, double nightRecovery,
                             double vacationNightRecovery,
                             double sundayExtraRecovery, SundayRules sunday) {
    }

    public record Dawn(DawnExercise exercise, DawnSleep sleep) {
    }

    public record DawnExercise(double fitness, double stamina) {
    }

    public record DawnSleep(double stamina) {
    }

    public record ClassAttitudeRule(String name, double academics, boolean scaleAcademicsByFriends,
                                    double stamina, double friendAffinity, double friendEventChance) {
    }

    public record FriendMultiplier(double base, double divisor) {
    }

    public record SundayRules(Rest rest, PartTime partTime, Meet meet) {
    }

    public record Rest(double stamina, int condition) {
    }

    public record PartTime(long money, double stamina) {
    }

    public record Meet(double affinity, List<Axis> targets) {
    }

    // ---- 자원 ----

    public record StaminaRules(double maxBase, double fitnessPivot, double fitnessDivisor,
                               double injuryRiskBelow, double injuryChancePerSlot, List<Integer> injuryWeeks,
                               double exhaustionBelow, double exhaustionCoachDelta) {
    }

    public record ConditionRules(int start, List<ConditionLevel> levels, double saturdayDropBelowStamina) {
    }

    public record ConditionLevel(String name, double training, double match) {
    }

    public record AcademicsRules(double start, double min, double max, double semesterWeeklyRate, Makeup makeup,
                                 double warningBelow) {
    }

    /** 나머지 공부: 한 주를 below 미만으로 마치면 다음 주 오후 팀 훈련 대신 */
    public record Makeup(double below, double academics, double stamina) {
    }

    public record RelationshipRules(double min, double max, AffinityStart start, LockedAxis girlfriend) {
    }

    /** 처음부터 열려 있는 축의 시작 관계도 */
    public record AffinityStart(double coach, double teammate, double family, double friend) {
        public double of(Axis axis) {
            return switch (axis) {
                case COACH -> coach;
                case TEAMMATE -> teammate;
                case FAMILY -> family;
                case FRIEND -> friend;
                case GIRLFRIEND, COMMON -> throw new IllegalArgumentException(axis + " 축은 처음부터 열려 있지 않습니다");
            };
        }
    }

    /** 잠긴 축: 열릴 때의 관계도, 일요일에 만나지 않은 주의 변화량 */
    public record LockedAxis(double unlockAffinity, double missedWeekChange) {
    }

    public record ResourceRules(long money, double reputation) {
    }

    // ---- 경기 ----

    public record MatchRules(int minutes, Selection selection, TeamGoals teamGoals, RoleValues<IntRange> sceneCount,
                             RoleValues<Double> staminaCost, Judgement judgement, PassiveRules passive,
                             double takeOnBonus, double pressingGoalChance, double penaltyWinChance,
                             RatingRules rating, CoachFeedback coachFeedback, Points points) {
    }

    public record RoleValues<T>(T starter, T sub, T bench) {
        public T of(MatchRole role) {
            return switch (role) {
                case STARTER -> starter;
                case SUB -> sub;
                case BENCH, ABSENT -> bench;
            };
        }
    }

    public record Selection(double coachWeight, double statWeight, int topStatCount, double starterRatio,
                            double subRatio) {
    }

    public record TeamGoals(double base, double perStrength, double min, double max) {
    }

    public record Judgement(double goalBase, double otherBase, double statFactor, double min, double max) {
    }

    public record PassiveRules(double pivot, double divisor, double min, double max, double totalMin,
                               double totalMax, int clutchFromMinute, int clutchMaxGoalDiff,
                               double growthPerMatch) {
    }

    public record RatingRules(double base, double goal, double assist, double otherSuccess, double goalFailure,
                              double failure,
                              double min, double max) {
    }

    public record CoachFeedback(double goodFrom, double goodDelta, double badBelow, double badDelta) {
    }

    public record Points(int win, int draw, int loss) {
    }

    // ---- 평판, 이벤트 ----

    public record ReputationRules(double gradeMultiplier, List<RatingReputation> rating, double goal,
                                  double assist, List<CupStage> cupStages) {
    }

    public record RatingReputation(double from, double value) {
    }

    public record CupStage(int teamsLeft, String name, double value) {
    }

    public record EventRules(double sundayChance, int cooldownWeeks, double trainingChance) {
    }

    // ---- 특성과 인연 ----

    /**
     * 훈련 성장 보너스. slots 가 비어 있으면 모든 칸, menus 가 비어 있으면 모든 메뉴.
     * values 는 1/2/3단계 값(비율).
     */
    public record TrainingBonus(List<TrainingSlot> slots, List<String> menus, List<Double> values) {
        public boolean appliesTo(TrainingSlot slot, String menuId) {
            return (slots == null || slots.isEmpty() || slots.contains(slot))
                    && (menus == null || menus.isEmpty() || menus.contains(menuId));
        }
    }

    public record TraitRules(List<Integer> tiers, List<TraitDef> list) {
    }

    /** semesterWeeklyRate: 단계별로 학기 중 주간 학업 감소율을 이 값으로 바꾼다 */
    public record TraitDef(String key, String name, String description, TrainingBonus trainingBonus,
                           List<Double> semesterWeeklyRate) {
    }

    public record BondRules(List<Double> tiers, List<BondDef> axes) {
    }

    /** nightRecovery: 단계별 밤 체력 회복 추가량 */
    public record BondDef(Axis axis, String description, TrainingBonus trainingBonus, List<Double> nightRecovery) {
    }
}
