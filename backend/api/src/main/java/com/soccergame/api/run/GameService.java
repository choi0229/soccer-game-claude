package com.soccergame.api.run;

import com.soccergame.domain.engine.ActionOutcome;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * 결과는 서버가 계산해 확정한다. 상태는 저장하지 않고, 시드와 순번별 요청을 재생해 매번 다시 만든다.
 * 새 행동은 재생한 상태에 적용해 보고, 성공했을 때만 다음 순번으로 기록한다.
 */
@Service
public class GameService {
    private final GameEngine engine;
    private final RunRepository repository;
    private final ObjectMapper json;
    private final SecureRandom seeds = new SecureRandom();

    public GameService(GameEngine engine, RunRepository repository, ObjectMapper json) {
        this.engine = engine;
        this.repository = repository;
        this.json = json;
    }

    public record Loaded(UUID runId, GameState state) {
    }

    /** schoolId 가 null 이면 설정 파일의 기본 학교 (학교를 고르기 전과 똑같이 동작한다) */
    @Transactional
    public Loaded create(Long requestedSeed, Integer schoolId) {
        long seed = requestedSeed != null ? requestedSeed : randomSeed();
        GameState state = engine.newGame(seed, schoolId);
        UUID id = UUID.randomUUID();
        repository.createRun(id, seed, schoolId);
        return new Loaded(id, state);
    }

    public long randomSeed() {
        return seeds.nextLong() >>> 12;
    }

    @Transactional(readOnly = true)
    public Loaded load(UUID id) {
        RunRepository.RunInfo run = repository.findRun(id).orElseThrow(() -> new RunNotFoundException(id));
        return new Loaded(id, replay(run, repository.actions(id)));
    }

    @Transactional
    public ActionResult act(UUID id, ActionRequest request) {
        RunRepository.RunInfo run = repository.lockRun(id).orElseThrow(() -> new RunNotFoundException(id));
        List<RunRepository.StoredAction> log = repository.actions(id);
        GameState state = replay(run, log);
        ActionOutcome outcome = engine.apply(state, request.toAction());
        repository.appendAction(id, log.size() + 1, request.type().name(), json.writeValueAsString(request));
        return new ActionResult(new Loaded(id, state), outcome);
    }

    public record ActionResult(Loaded loaded, ActionOutcome outcome) {
    }

    @Transactional(readOnly = true)
    public List<RunRepository.StoredAction> actions(UUID id) {
        repository.findRun(id).orElseThrow(() -> new RunNotFoundException(id));
        return repository.actions(id);
    }

    /** 생성 기록(시드, 학교)과 순번별 요청만으로 판을 처음부터 다시 만든다. */
    public GameState replay(RunRepository.RunInfo run, List<RunRepository.StoredAction> log) {
        GameState state = engine.newGame(run.seed(), run.schoolId());
        int expected = 1;
        for (RunRepository.StoredAction stored : log) {
            if (stored.seq() != expected++) {
                throw new IllegalStateException("행동 기록 순번이 비어 있습니다: " + stored.seq());
            }
            engine.apply(state, json.readValue(stored.payload(), ActionRequest.class).toAction());
        }
        return state;
    }
}
