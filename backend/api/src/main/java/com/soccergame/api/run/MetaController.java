package com.soccergame.api.run;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.GameEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판을 시작하기 전 화면(시작 화면)이 쓰는 설정 값 */
@RestController
public class MetaController {
    private final GameConfig config;
    private final GameCalendar calendar;

    public MetaController(GameEngine engine) {
        this.config = engine.config();
        this.calendar = engine.calendar();
    }

    public record Meta(String position, String archetype, int weeksPerYear, String startLabel, String endLabel) {
    }

    @GetMapping("/api/meta")
    public Meta meta() {
        int last = calendar.weeksPerYear() - 1;
        return new Meta(config.rules().archetype().position(), config.rules().archetype().name(),
                calendar.weeksPerYear(), calendar.month(0) + "월 " + calendar.weekOfMonth(0) + "주",
                calendar.month(last) + "월 " + calendar.weekOfMonth(last) + "주");
    }
}
