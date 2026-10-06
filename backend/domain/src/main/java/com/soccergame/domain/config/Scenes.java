package com.soccergame.domain.config;

import java.util.List;

/** config/scenes.yaml 의 구조. */
public record Scenes(List<SceneDef> scenes) {

    public enum Outcome {
        /** 성공하면 골 */
        GOAL,
        /** 성공하면 extraScene 장면 1개를 바로 추가 */
        EXTRA_SCENE,
        /** 성공하면 다음 골 판정 장면 성공률 + takeOnBonus */
        NEXT_SHOT_BONUS,
        /** 성공하면 도움 (팀 득점 +1) */
        ASSIST,
        /** 성공하면 pressingGoalChance 확률로 팀 득점 +1 */
        PRESS
    }

    public record SceneDef(String id, String name, List<String> stats, Outcome outcome, String extraScene,
                           String success, String failure) {
        public boolean isGoalScene() {
            return outcome == Outcome.GOAL;
        }
    }
}
