package com.soccergame.domain.config;

import java.util.List;
import java.util.Map;

/** config/clutch-moments.yaml 의 구조: 경기 중 선택(승부처). */
public record ClutchMoments(Chance chance, Map<Style, String> styles, List<MomentDef> moments) {

    public enum Half {
        FIRST, SECOND
    }

    public enum ScoreState {
        LEADING, TIED, TRAILING
    }

    public enum Style {
        AGGRESSIVE, BALANCED, SAFE
    }

    public enum Outcome {
        /** 골 */
        GOAL,
        /** 도움 (팀 득점 +1) */
        ASSIST,
        /** extraScene 슈팅 장면 1개 추가 */
        EXTRA_SHOT,
        /** teamGoalChance 확률로 팀 득점 +1 */
        TEAM_GOAL_CHANCE
    }

    /** 출전 역할별 승부처 확률 */
    public record Chance(double starter, double sub) {
    }

    public record Condition(Half half, ScoreState score) {
    }

    public record MomentDef(String id, String title, String situation, Condition condition,
                           List<MomentChoice> choices) {
    }

    public record MomentChoice(String text, Style style, List<String> stats, double base, Success success,
                               double failRating, String trait) {
    }

    public record Success(Outcome outcome, String extraScene, Double teamGoalChance) {
    }
}
