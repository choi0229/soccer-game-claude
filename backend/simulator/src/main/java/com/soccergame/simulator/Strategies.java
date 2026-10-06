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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 명세의 전략 3종 */
public final class Strategies {
    private Strategies() {
    }

    public static List<Strategy> all() {
        return List.of(new RandomStrategy(), new TrainingFocus(), new Balanced(), new AcademicNeglect());
    }

    private static Map<TrainingSlot, String> allSlots(String menuId) {
        Map<TrainingSlot, String> menus = new EnumMap<>(TrainingSlot.class);
        for (TrainingSlot slot : TrainingSlot.values()) {
            menus.put(slot, menuId);
        }
        return menus;
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
            Axis target = activity == SundayActivity.MEET
                    ? choice.pick(engine.config().rules().daily().sunday().meet().targets()) : null;
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
                List.of(ClassAttitude.FOCUS, ClassAttitude.DOZE, ClassAttitude.TEACHER, ClassAttitude.FRIENDS);

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

        @Override
        public Action.Sunday sunday(GameState s, GameEngine engine, Rng choice) {
            return switch (s.week % 4) {
                case 0 -> new Action.Sunday(SundayActivity.REST, null);
                case 1 -> new Action.Sunday(SundayActivity.MEET, Axis.COACH);
                case 2 -> new Action.Sunday(SundayActivity.MEET, Axis.TEAMMATE);
                default -> new Action.Sunday(SundayActivity.MEET, Axis.FAMILY);
            };
        }
    }
}
