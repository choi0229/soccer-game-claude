package com.soccergame.api.view;

import com.soccergame.domain.engine.ActionOutcome;
import com.soccergame.domain.engine.Phase;
import com.soccergame.domain.engine.SeasonSummary;
import com.soccergame.domain.match.MatchRecord;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 화면이 그대로 그리는 판 상태. 모든 수치는 서버가 계산한 값이다. */
public record GameView(UUID runId, long seed, int actionCount, String fingerprint, Phase phase, DateView date,
                       SchoolView school, ResourcesView resources, List<StatView> stats, SelectionsView selections,
                       List<SlotView> slots, MatchPreview matchToday, OptionsView options, List<StandingView> league, CupView cup,
                       List<TraitView> traits, List<BondView> bonds,
                       EventView pendingEvent, ActionOutcome lastOutcome, List<MatchRecord> matches,
                       SeasonSummary summary) {

    public record DateView(int week, int weeksPerYear, int month, int weekOfMonth, String day, String dayLabel,
                           String label, String term, boolean vacation) {
    }

    public record MatchPreview(String competition, String round, int opponentId, String opponentName,
                               String opponentType, double opponentStrength, String opponentDefender, boolean home) {
    }

    public record SchoolView(int id, String name, String typeName, double strength, String region) {
    }

    public record ResourcesView(double stamina, double maxStamina, int condition, String conditionName, long money,
                                double academics, double reputation, Map<String, Double> affinity,
                                double friendMultiplier, boolean injured, String injuredUntil,
                                double selectionScore, String expectedRole, StaminaInfo staminaInfo,
                                AcademicsInfo academicsInfo) {
    }

    /**
     * 체력 설명용 서버 값. nightRecovery 는 오늘 밤(학기 중/방학) 회복량, sundayExtraRecovery 는 일요일 추가분.
     * lowBelow 는 부상 위험이 생기는 체력.
     */
    public record StaminaInfo(double nightRecovery, double sundayExtraRecovery, double lowBelow, String maxFormula) {
    }

    /** warning 은 학업 성취가 경고 기준 미만일 때의 안내 문구 (아니면 null) */
    public record AcademicsInfo(double makeupBelow, double warningBelow, String warning) {
    }

    public record StatView(String key, String name, String group, boolean primary, boolean passive, double value,
                           double initial, String grade) {
    }

    public record SelectionsView(String dawn, String classAttitude, Map<String, String> menus) {
    }

    /**
     * 오늘의 한 칸. kind: DAWN(새벽 선택) / CLASS(수업 태도 선택) / TRAINING(메뉴 선택) / FIXED(선택 없음)
     * menuSlot 은 TRAINING 일 때 메뉴를 저장하는 칸 이름.
     */
    public record SlotView(String key, String label, String kind, String activity, String menuSlot, String note) {
    }

    public record OptionsView(List<Option> dawn, List<Option> classAttitudes, List<MenuOption> menus,
                              List<Option> sundayActivities, List<Option> meetTargets) {
    }

    public record Option(String key, String name, String description) {
    }

    public record MenuOption(String id, String name, List<String> stats, double growthMultiplier) {
    }

    public record StandingView(int rank, int schoolId, String name, String typeName, double strength, int played,
                               int won, int drawn, int lost, int goalsFor, int goalsAgainst, int goalDiff,
                               int points, boolean player) {
    }

    public record CupView(String name, boolean playerAlive, int roundsPlayed, List<String> stagesReached) {
    }

    public record EventView(String eventId, String axis, String title, String body, String source,
                            List<ChoiceView> choices) {
    }

    /** trait: 고르면 오르는 특성 이름 (없으면 null) */
    public record ChoiceView(String text, String trait) {
    }

    /**
     * 특성. nextAt 은 다음 단계 기준 점수(최고 단계면 null), remaining 은 남은 점수.
     * tierEffects 는 1/2/3단계 효과 설명.
     */
    public record TraitView(String key, String name, int score, int tier, Integer nextAt, Integer remaining,
                            String effect, List<String> tierEffects) {
    }

    /** 인연. locked 면 affinity 와 tier 는 의미 없다. nextAt 은 다음 단계 관계도(최고 단계면 null). */
    public record BondView(String axis, String label, boolean locked, Double affinity, int tier, Double nextAt,
                           String effect, List<String> tierEffects) {
    }
}
