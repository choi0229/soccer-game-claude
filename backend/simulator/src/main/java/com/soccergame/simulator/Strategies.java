package com.soccergame.simulator;

import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.config.TrainingMenus.TrainingMenu;
import com.soccergame.domain.engine.Action;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.SundayActivity;
import com.soccergame.domain.model.TrainingSlot;
import com.soccergame.domain.random.Rng;

import com.soccergame.domain.config.Events;
import com.soccergame.domain.config.Events.EventDef;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Set;
import java.util.List;
import java.util.Map;

/** 명세의 전략 3종 */
public final class Strategies {
    private Strategies() {
    }

    public static List<Strategy> all() {
        return List.of(new RandomStrategy(), new TrainingFocus(), new Balanced(), new AcademicNeglect(), new GrowthMax());
    }

    private static Map<TrainingSlot, String> allSlots(String menuId) {
        Map<TrainingSlot, String> menus = new EnumMap<>(TrainingSlot.class);
        for (TrainingSlot slot : TrainingSlot.values()) {
            menus.put(slot, menuId);
        }
        return menus;
    }

    /** 지금 만날 수 있는 상대 (잠긴 축 제외) */
    static List<Axis> meetable(GameState s, GameEngine engine) {
        return engine.config().rules().daily().sunday().meet().targets().stream()
                .filter(s.affinity::containsKey).toList();
    }

    /**
     * 성장 극대화: 일과는 훈련 위주와 같되 학업 성취가 40 이상이면 수업에서 친구와 어울린다.
     * 이벤트는 노력파·승부사 선택지를 우선(없으면 무작위), 소개 이벤트에서는 연락처를 받는다.
     * 일요일은 감독 → 동료 → 여자친구(열린 뒤) 순환.
     */
    static final class GrowthMax extends TrainingFocus {
        private static final Set<String> PREFERRED = Set.of("hardWorker", "competitor");

        @Override
        public String name() {
            return "성장 극대화";
        }

        @Override
        ClassAttitude classAttitude(GameState s) {
            return s.academics < 40 ? ClassAttitude.FOCUS : ClassAttitude.FRIENDS;
        }

        @Override
        public Action.Sunday sunday(GameState s, GameEngine engine, Rng choice) {
            List<Axis> order = new ArrayList<>(List.of(Axis.COACH, Axis.TEAMMATE));
            if (s.affinity.containsKey(Axis.GIRLFRIEND)) {
                order.add(Axis.GIRLFRIEND);
            }
            return new Action.Sunday(SundayActivity.MEET, order.get(s.week % order.size()));
        }

        @Override
        public int eventChoice(GameState s, EventDef event, Rng choice) {
            List<Events.Choice> choices = event.choices();
            List<Integer> preferred = new ArrayList<>();
            for (int i = 0; i < choices.size(); i++) {
                Events.Choice c = choices.get(i);
                if (c.effects() != null && c.effects().unlockAxis() != null) {
                    return i;
                }
                if (c.trait() != null && PREFERRED.contains(c.trait())) {
                    preferred.add(i);
                }
            }
            return preferred.isEmpty() ? choice.nextInt(choices.size()) : choice.pick(preferred);
        }
    }

    /** 1년 동안 지난 평일 수 (월요일 3월 1주 = 0) */
    private static int weekdayIndex(GameState s) {
        return s.week * 5 + Math.min(s.day.ordinal(), 4);
    }

    /** 무작위: 새벽, 수업 태도, 칸별 훈련 메뉴, 일요일 행동(만날 상대 포함)을 모두 균등 무작위 */
    static final class RandomStrategy implements Strategy {
        @Override
        public String name() {
            return "무작위";
        }

        @Override
        public Action.Day day(GameState s, GameEngine engine, Rng choice) {
            List<TrainingMenu> menus = engine.config().trainingMenus().menus();
            Map<TrainingSlot, String> picked = new EnumMap<>(TrainingSlot.class);
            for (TrainingSlot slot : TrainingSlot.values()) {
                picked.put(slot, choice.pick(menus).id());
            }
            return new Action.Day(choice.pick(List.of(DawnChoice.values())),
                    choice.pick(List.of(ClassAttitude.values())), picked);
        }

        @Override
        public Action.Sunday sunday(GameState s, GameEngine engine, Rng choice) {
            SundayActivity activity = choice.pick(List.of(SundayActivity.values()));
            Axis target = activity == SundayActivity.MEET ? choice.pick(meetable(s, engine)) : null;
            return new Action.Sunday(activity, target);
        }
    }

    /**
     * 훈련 위주: 새벽은 체력 50 이상이면 개인 운동, 아니면 더 자기.
     * 수업은 학업 성취 40 미만이면 집중, 아니면 졸기. 메뉴는 파워와 제공권을 날마다 번갈아 전 칸에. 일요일은 휴식.
     */
    static class TrainingFocus implements Strategy {
        @Override
        public String name() {
            return "훈련 위주";
        }

        ClassAttitude classAttitude(GameState s) {
            return s.academics < 40 ? ClassAttitude.FOCUS : ClassAttitude.DOZE;
        }

        @Override
        public Action.Day day(GameState s, GameEngine engine, Rng choice) {
            DawnChoice dawn = s.stamina >= 50 ? DawnChoice.EXERCISE : DawnChoice.SLEEP;
            ClassAttitude attitude = classAttitude(s);
            String menu = weekdayIndex(s) % 2 == 0 ? "power" : "aerial";
            return new Action.Day(dawn, attitude, allSlots(menu));
        }

        @Override
        public Action.Sunday sunday(GameState s, GameEngine engine, Rng choice) {
            return new Action.Sunday(SundayActivity.REST, null);
        }
    }

    /** 학업 방치: 훈련 위주와 같지만 수업은 항상 졸기 */
    static final class AcademicNeglect extends TrainingFocus {
        @Override
        public String name() {
            return "학업 방치";
        }

        @Override
        ClassAttitude classAttitude(GameState s) {
            return ClassAttitude.DOZE;
        }
    }

    /**
     * 균형: 새벽은 월·수·금만 개인 운동. 수업은 집중→졸기→선생님→친구 순환.
     * 메뉴는 7종을 날마다 순서대로 (그날 전 칸 같은 메뉴). 일요일은 휴식→감독→동료→가족 순환.
     */
    static final class Balanced implements Strategy {
        private static final List<ClassAttitude> CLASS_ORDER =
                List.of(ClassAttitude.FOCUS, ClassAttitude.DOZE, ClassAttitude.QUESTION, ClassAttitude.FRIENDS);

        @Override
        public String name() {
            return "균형";
        }

        @Override
        public Action.Day day(GameState s, GameEngine engine, Rng choice) {
            boolean exerciseDay = s.day == Weekday.MON || s.day == Weekday.WED || s.day == Weekday.FRI;
            int index = weekdayIndex(s);
            List<TrainingMenu> menus = engine.config().trainingMenus().menus();
            return new Action.Day(exerciseDay ? DawnChoice.EXERCISE : DawnChoice.SLEEP,
                    CLASS_ORDER.get(index % CLASS_ORDER.size()),
                    allSlots(menus.get(index % menus.size()).id()));
        }

        /** 휴식 → 감독 → 동료 → 가족 → 여자친구(열린 뒤부터) 순환 */
        @Override
        public Action.Sunday sunday(GameState s, GameEngine engine, Rng choice) {
            List<Axis> order = new ArrayList<>(List.of(Axis.COACH, Axis.TEAMMATE, Axis.FAMILY));
            if (s.affinity.containsKey(Axis.GIRLFRIEND)) {
                order.add(Axis.GIRLFRIEND);
            }
            int i = s.week % (order.size() + 1);
            return i == 0 ? new Action.Sunday(SundayActivity.REST, null)
                    : new Action.Sunday(SundayActivity.MEET, order.get(i - 1));
        }
    }
}
