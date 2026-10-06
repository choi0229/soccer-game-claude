package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.competition.Cup;
import com.soccergame.domain.competition.League;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.School;
import com.soccergame.domain.model.Stats;
import com.soccergame.domain.random.Rng;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 한 판의 전체 상태. 규칙은 GameEngine 과 그 협력 클래스에 있고, 이 클래스는 값만 담는다.
 * 같은 시드와 같은 행동 순서로 만들면 항상 같은 상태가 된다.
 */
public final class GameState {
    public final long seed;
    /** 게임 판정 전용 난수 흐름 */
    public final Rng rng;

    public int week;
    public Weekday day = Weekday.MON;
    public boolean finished;
    public int actionCount;

    public final List<School> schools = new ArrayList<>();
    public int playerSchoolId;
    public League league;
    public Cup cup;

    public Stats stats;
    public Stats initialStats;
    public double stamina;
    public int condition;
    public long money;
    public double academics;
    public double reputation;
    /** 열린 축의 관계도. 잠긴 축(여자친구)은 열리기 전까지 키가 없다 */
    public final Map<Axis, Double> affinity = new EnumMap<>(Axis.class);
    /** 잠겼던 축이 열린 주 */
    public final Map<Axis, Integer> axisUnlockedWeek = new EnumMap<>(Axis.class);
    /** 특성 점수 (설정 파일 순서) */
    public final Map<String, Integer> traitScores = new LinkedHashMap<>();

    /** 이 절대 일 번호 전까지 부상 (-1 이면 부상 아님) */
    public int injuredUntilDay = -1;
    /** 오늘 체력 고갈로 남은 훈련에서 제외되었는지 */
    public boolean excludedToday;

    public final Selections selections = new Selections();
    /** 메뉴별 누적 훈련 횟수 (이번 버전은 저장만 한다) */
    public final Map<String, Integer> menuTrainingCounts = new LinkedHashMap<>();

    public final Set<String> flags = new LinkedHashSet<>();
    /** 이벤트 id → 마지막으로 발생한 주 */
    public final Map<String, Integer> eventLastWeek = new LinkedHashMap<>();
    public final Deque<PendingEvent> pendingEvents = new ArrayDeque<>();
    public final List<String> eventHistory = new ArrayList<>();

    /** 나머지 공부 주 → 그 직전 주를 마칠 때의 학업 성취 (이유 표시용) */
    public final Map<Integer, Double> makeupWeeks = new TreeMap<>();

    public final List<MatchRecord> matches = new ArrayList<>();
    public final List<String> cupStagesReached = new ArrayList<>();

    public final Metrics metrics;
    public ActionOutcome lastOutcome;

    public GameState(long seed, Rng rng, int weeks) {
        this.seed = seed;
        this.rng = rng;
        this.metrics = new Metrics(weeks);
    }

    public School school(int id) {
        return schools.get(id - 1);
    }

    public School playerSchool() {
        return school(playerSchoolId);
    }

    public int absoluteDay() {
        return week * 7 + day.ordinal();
    }

    public boolean isInjured() {
        return injuredUntilDay > absoluteDay();
    }

    public MatchRecord lastMatch() {
        return matches.isEmpty() ? null : matches.getLast();
    }
}
