package com.soccergame.api.run;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.GameEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/** 판을 시작하기 전 화면(시작 화면)이 쓰는 설정 값 */
@RestController
public class MetaController {
    private final GameConfig config;
    private final GameCalendar calendar;
    private final GameEngine engine;
    private final GameService service;

    public MetaController(GameEngine engine, GameService service) {
        this.config = engine.config();
        this.calendar = engine.calendar();
        this.engine = engine;
        this.service = service;
    }

    /** leagueMatches: 주말리그 경기 수, tournaments: 대회 이름 */
    public record Meta(String position, String archetype, int weeksPerYear, String startLabel, String endLabel,
                       int leagueMatches, List<String> tournaments) {
    }

    @GetMapping("/api/meta")
    public Meta meta() {
        int last = calendar.weeksPerYear() - 1;
        return new Meta(config.rules().archetype().position(), config.rules().archetype().name(),
                calendar.weeksPerYear(), calendar.month(0) + "월 " + calendar.weekOfMonth(0) + "주",
                calendar.month(last) + "월 " + calendar.weekOfMonth(last) + "주", calendar.leagueRounds(),
                calendar.tournaments().stream().map(t -> t.name()).toList());
    }

    public record SchoolOption(int id, String name, String region) {
    }

    public record SchoolType(String key, String name, double strength, List<SchoolOption> schools) {
    }

    /** seed: 학교 이름을 만든 시드 (요청에 없으면 서버가 무작위로 정한 값), defaultSchoolId: 기본 학교 */
    public record SchoolList(long seed, boolean randomSeed, int defaultSchoolId, List<SchoolType> types) {
    }

    /** 학교 이름은 시드로 정해지므로 시드별 목록을 준다. 학교 번호와 유형·권역은 시드와 상관없이 같다. */
    @GetMapping("/api/schools")
    public SchoolList schools(@RequestParam(required = false) Long seed) {
        long s = seed != null ? seed : service.randomSeed();
        var state = engine.newGame(s);
        var regions = config.schools().regions().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.id(), r -> r.name()));
        List<SchoolType> types = config.schools().types().stream().map(t -> new SchoolType(t.key(), t.name(),
                t.strength(), state.schools.stream().filter(x -> x.typeKey().equals(t.key()))
                .map(x -> new SchoolOption(x.id(), x.name(), regions.get(x.region()))).toList())).toList();
        return new SchoolList(s, seed == null, state.playerSchoolId, types);
    }
}
