package com.soccergame.domain.competition;

import com.soccergame.domain.random.Rng;

import java.util.ArrayList;
import java.util.List;

/** 단판 토너먼트. 무작위 추첨 후 대진표 순서대로 짝을 짓는다. */
public final class Cup {
    private final List<Integer> entrants;
    private List<Integer> alive;
    private final List<List<FixtureResult>> results = new ArrayList<>();

    private Cup(List<Integer> alive) {
        this.entrants = List.copyOf(alive);
        this.alive = alive;
    }

    /** 추첨 때의 참가 학교 (대진표 순서) */
    public List<Integer> entrants() {
        return entrants;
    }

    public static Cup draw(List<Integer> teamIds, Rng rng) {
        List<Integer> bracket = new ArrayList<>(teamIds);
        rng.shuffle(bracket);
        return new Cup(bracket);
    }

    public List<Integer> alive() {
        return List.copyOf(alive);
    }

    public boolean isAlive(int teamId) {
        return alive.contains(teamId);
    }

    public int roundsPlayed() {
        return results.size();
    }

    public List<List<FixtureResult>> results() {
        return List.copyOf(results);
    }

    /** 현재 라운드의 대진: [0] vs [1], [2] vs [3] ... */
    public List<League.Fixture> currentPairs() {
        List<League.Fixture> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < alive.size(); i += 2) {
            pairs.add(new League.Fixture(alive.get(i), alive.get(i + 1)));
        }
        return pairs;
    }

    public void record(List<FixtureResult> roundResults) {
        List<Integer> next = new ArrayList<>();
        for (FixtureResult r : roundResults) {
            next.add(r.winnerId());
        }
        results.add(List.copyOf(roundResults));
        alive = next;
    }

    public static String roundName(int teamsInRound) {
        return teamsInRound == 2 ? "결승" : teamsInRound == 4 ? "4강" : teamsInRound + "강";
    }
}
