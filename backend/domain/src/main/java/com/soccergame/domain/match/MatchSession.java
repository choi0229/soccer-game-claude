package com.soccergame.domain.match;

import com.soccergame.domain.config.ClutchMoments.MomentDef;
import com.soccergame.domain.config.Scenes.SceneDef;

import java.util.ArrayList;
import java.util.List;

/**
 * 진행 중인 경기 하나. 승부처에서 멈췄다가 선택을 받아 이어서 계산한다.
 * MatchEngine 만 상태를 바꾼다.
 */
public final class MatchSession {
    final MatchEngine.Input input;
    final int teamGoals;
    final int opponentGoals;
    final List<Integer> ourMinutes;
    final List<Integer> theirMinutes;
    final List<SceneDef> picked;
    /** 승부처로 대체할 장면 순번 (-1 이면 없음) */
    final int clutchSlot;

    int index;
    Boolean previous;
    boolean takeOnPending;
    int playerGoals;
    int assists;
    int pressGoals;
    int clutchTeamGoals;
    int successes;
    int failures;
    int goalFailures;
    double ratingDelta;
    final List<SceneLog> logs = new ArrayList<>();
    final List<Integer> extraOurGoals = new ArrayList<>();

    /** 선택을 기다리는 승부처 */
    MomentDef moment;
    int momentMinute;
    ClutchLog clutchLog;
    MatchEngine.Result result;

    MatchSession(MatchEngine.Input input, int teamGoals, int opponentGoals, List<Integer> ourMinutes,
                 List<Integer> theirMinutes, List<SceneDef> picked, int clutchSlot) {
        this.input = input;
        this.teamGoals = teamGoals;
        this.opponentGoals = opponentGoals;
        this.ourMinutes = ourMinutes;
        this.theirMinutes = theirMinutes;
        this.picked = picked;
        this.clutchSlot = clutchSlot;
    }

    public boolean awaitingChoice() {
        return moment != null;
    }

    public boolean finished() {
        return result != null;
    }

    public MomentDef moment() {
        return moment;
    }

    public int momentMinute() {
        return momentMinute;
    }

    public MatchEngine.Result result() {
        return result;
    }

    public MatchEngine.Input input() {
        return input;
    }
}
