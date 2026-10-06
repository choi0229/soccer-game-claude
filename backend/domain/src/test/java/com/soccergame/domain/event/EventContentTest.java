package com.soccergame.domain.event;

import com.soccergame.domain.TestConfig;
import com.soccergame.domain.config.Events.Effects;
import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.model.Axis;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 이벤트 데이터가 명세의 분량과 효과 크기를 지키는지 */
class EventContentTest {
    private final List<EventDef> events = TestConfig.load().events().events();

    @Test
    void thirtyEventsWithRequiredAxisCounts() {
        assertThat(events).hasSize(30);
        Map<Axis, Long> byAxis = events.stream().collect(Collectors.groupingBy(EventDef::axis, Collectors.counting()));
        assertThat(byAxis).containsEntry(Axis.COACH, 8L).containsEntry(Axis.TEAMMATE, 8L)
                .containsEntry(Axis.FAMILY, 5L).containsEntry(Axis.SCHOOL, 6L).containsEntry(Axis.COMMON, 3L);
        assertThat(events.stream().map(EventDef::id).distinct()).hasSize(30);
    }

    @Test
    void atLeastTwelveRepeatable() {
        assertThat(events.stream().filter(e -> !e.once()).count()).isGreaterThanOrEqualTo(12);
    }

    @Test
    void choicesAndEffectSizesWithinSpec() {
        for (EventDef e : events) {
            assertThat(e.choices()).as(e.id()).hasSizeBetween(2, 3);
            assertThat(e.title()).isNotBlank();
            assertThat(e.body()).isNotBlank();
            for (var choice : e.choices()) {
                Effects fx = choice.effects();
                assertThat(fx).as(e.id()).isNotNull();
                if (fx.stat() != null) {
                    fx.stat().values().forEach(v -> assertThat(Math.abs(v)).as(e.id()).isBetween(0.5, 2.0));
                }
                for (Double a : new Double[]{fx.coachAffinity(), fx.teammateAffinity(), fx.familyAffinity(), fx.schoolAffinity()}) {
                    if (a != null) assertThat(Math.abs(a)).as(e.id()).isBetween(2.0, 6.0);
                }
                if (fx.stamina() != null) assertThat(Math.abs(fx.stamina())).as(e.id()).isBetween(5.0, 20.0);
                if (fx.academics() != null) assertThat(Math.abs(fx.academics())).as(e.id()).isBetween(1.0, 5.0);
                if (fx.condition() != null) assertThat(Math.abs(fx.condition())).as(e.id()).isEqualTo(1);
            }
        }
    }

    @Test
    void meetTargetsAndSchoolHaveUnconditionalOrBroadEvents() {
        // 사람 만나기와 수업 이벤트가 대부분의 상황에서 무언가를 띄울 수 있도록, 축마다 반복 가능한 이벤트가 있다
        for (Axis axis : List.of(Axis.COACH, Axis.TEAMMATE, Axis.FAMILY, Axis.SCHOOL)) {
            assertThat(events.stream().filter(e -> e.axis() == axis && !e.once())).as(axis.name()).isNotEmpty();
        }
    }
}
