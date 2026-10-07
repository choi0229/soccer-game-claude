package com.soccergame.api.run;

import com.soccergame.api.view.GameView;
import com.soccergame.api.view.ViewMapper;
import com.soccergame.domain.engine.ActionOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
public class RunController {
    private final GameService service;
    private final ViewMapper mapper;

    public RunController(GameService service, ViewMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** schoolId 가 없으면 설정 파일의 기본 학교 */
    public record CreateRunRequest(Long seed, Integer schoolId) {
    }

    public record ActionResponse(GameView view, ActionOutcome outcome) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameView create(@RequestBody(required = false) CreateRunRequest request) {
        GameService.Loaded loaded = service.create(request == null ? null : request.seed(),
                request == null ? null : request.schoolId());
        return mapper.toView(loaded.runId(), loaded.state());
    }

    @GetMapping("/{id}")
    public GameView get(@PathVariable UUID id) {
        GameService.Loaded loaded = service.load(id);
        return mapper.toView(loaded.runId(), loaded.state());
    }

    @PostMapping("/{id}/actions")
    public ActionResponse act(@PathVariable UUID id, @RequestBody ActionRequest request) {
        GameService.ActionResult result = service.act(id, request);
        return new ActionResponse(mapper.toView(id, result.loaded().state()), result.outcome());
    }

    /** 이 판의 행동 기록 (순번, 종류, 요청 JSON). 시드와 이 목록만으로 판을 재현할 수 있다. */
    @GetMapping("/{id}/actions")
    public List<RunRepository.StoredAction> actions(@PathVariable UUID id) {
        return service.actions(id);
    }
}
