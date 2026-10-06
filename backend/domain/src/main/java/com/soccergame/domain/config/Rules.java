package com.soccergame.domain.config;

import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.MatchRole;

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
        EventRules events) {

    public record WeekRef(int month, int week) {
    }

    public record IntRange(int min, int max) {
    }

    // ---- 캘린더 ----

    public record CalendarRules(int weeksPerYear, int weeksPerMonth, int startMonth,
                                List<Vacation> vacations, LeagueSchedule league, CupSchedule cup) {
    }

    public record Vacation(String name, WeekRef from, WeekRef to) {
    }

    public record LeagueSchedule(Weekday matchDay, List<LeagueBlock> blocks) {
    }

    public record LeagueBlock(int fromRound, int toRound, WeekRef from, WeekRef to) {
    }

    public record CupSchedule(String name, List<CupDay> matchDays) {
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

    public record TrainingRules(SlotTraining team, SlotTraining personal, List<DecayStep> decay) {
    }

    public record SlotTraining(double baseGrowth, double stamina) {
    }

    public record DecayStep(double below, double factor) {
    }

    public record DailyRules(DawnChoice defaultDawn, ClassAttitude defaultClassAttitude, Dawn dawn, Map<ClassAttitude, ClassAttitudeRule> classAttitudes,
                             SchoolLifeMultiplier schoolLifeMultiplier, double nightRecovery,
                             double sundayExtraRecovery, SundayRules sunday) {
    }

    public record Dawn(DawnExercise exercise, DawnSleep sleep) {
    }

    public record DawnExercise(double fitness, double stamina) {
    }

    public record DawnSleep(double stamina) {
    }

    public record ClassAttitudeRule(String name, double academics, boolean scaleAcademicsBySchoolLife,
                                    double stamina, double schoolAffinity, double schoolEventChance) {
    }

    public record SchoolLifeMultiplier(double base, double divisor) {
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

    public record AcademicsRules(double start, double min, double max, double remedialGain,
                                 double semesterWeeklyChange, List<AcademicCheck> checks) {
    }

    public record AcademicCheck(WeekRef at, double below, List<WeekRef> remedialWeeks) {
    }

    public record RelationshipRules(double min, double max, AffinityStart start) {
    }

    public record AffinityStart(double coach, double teammate, double family, double school) {
        public double of(Axis axis) {
            return switch (axis) {
                case COACH -> coach;
                case TEAMMATE -> teammate;
                case FAMILY -> family;
                case SCHOOL -> school;
                case COMMON -> throw new IllegalArgumentException("common 축에는 관계도가 없습니다");
            };
        }
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

    public record Selection(double coachWeight, double statWeight, int topStatCount, double starterMargin,
                            double subMargin) {
    }

    public record TeamGoals(double base, double perStrength, double min, double max) {
    }

    public record Judgement(double goalBase, double otherBase, double statFactor, double min, double max) {
    }

    public record PassiveRules(double pivot, double divisor, double min, double max, double totalMin,
                               double totalMax, int clutchFromMinute, int clutchMaxGoalDiff,
                               double growthPerMatch) {
    }

    public record RatingRules(double base, double goal, double assist, double otherSuccess, double failure,
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

    public record EventRules(double sundayChance, int cooldownWeeks) {
    }
}
