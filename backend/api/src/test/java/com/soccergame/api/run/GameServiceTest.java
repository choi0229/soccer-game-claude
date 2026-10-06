package com.soccergame.api.run;

import com.soccergame.domain.config.ConfigLoader;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.engine.InvalidActionException;
import com.soccergame.domain.engine.Phase;
import com.soccergame.domain.engine.StateFingerprint;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.SundayActivity;
import com.soccergame.domain.model.TrainingSlot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameServiceTest {
    private final GameEngine engine = new GameEngine(ConfigLoader.load(Path.of(System.getProperty("game.config.dir"))));
    private final InMemoryRunRepository repo = new InMemoryRunRepository();
    private final GameService service = new GameService(engine, repo, JsonMapper.builder().build());

    private static ActionRequest day(DawnChoice dawn, ClassAttitude attitude, Map<TrainingSlot, String> menus) {
        return new ActionRequest(ActionRequest.Type.DAY, dawn, attitude, menus, null, null, null, null);
    }

    /** 화면에서 하듯 요청을 보내며 1년을 진행한다. 선택은 주차에 따라 바꾼다. */
    private UUID playYear(long seed) {
        UUID id = service.create(seed).runId();
        int step = 0;
        while (true) {
            GameState s = service.load(id).state();
            Phase phase = engine.phase(s);
            ActionRequest request = switch (phase) {
                case FINISHED -> null;
                case EVENT -> new ActionRequest(ActionRequest.Type.EVENT_CHOICE, null, null, null, null, null,
                        s.pendingEvents.peekFirst().eventId(), step % 2);
                case SUNDAY -> step % 3 == 0
                        ? new ActionRequest(ActionRequest.Type.SUNDAY, null, null, null, SundayActivity.MEET, Axis.COACH, null, null)
                        : new ActionRequest(ActionRequest.Type.SUNDAY, null, null, null, SundayActivity.REST, null, null, null);
                default -> step % 5 == 0
                        ? day(DawnChoice.EXERCISE, ClassAttitude.FRIENDS, Map.of(TrainingSlot.NIGHT, "power"))
                        : day(null, null, Map.of());
            };
            if (request == null) {
                return id;
            }
            service.act(id, request);
            step++;
        }
    }

    @Test
    void sameSeedAndSameRequestsReproduceTheRun() {
        UUID a = playYear(2024);
        UUID b = playYear(2024);
        GameState sa = service.load(a).state();
        GameState sb = service.load(b).state();
        assertThat(sa.finished).isTrue();
        assertThat(StateFingerprint.of(sa)).isEqualTo(StateFingerprint.of(sb));
        assertThat(sa.matches).isEqualTo(sb.matches);
        assertThat(repo.actions.get(a)).extracting(RunRepository.StoredAction::payload)
                .isEqualTo(repo.actions.get(b).stream().map(RunRepository.StoredAction::payload).toList());
    }

    @Test
    void actionsAreAppendedWithIncreasingSeq() {
        UUID id = service.create(1L).runId();
        service.act(id, day(DawnChoice.SLEEP, null, Map.of()));
        service.act(id, day(null, ClassAttitude.DOZE, Map.of(TrainingSlot.AFTERNOON, "aerial")));
        assertThat(service.actions(id)).extracting(RunRepository.StoredAction::seq).containsExactly(1, 2);
        assertThat(service.actions(id).get(1).payload()).contains("\"DOZE\"").contains("aerial");
    }

    @Test
    void rejectedActionIsNotRecorded() {
        UUID id = service.create(1L).runId();
        assertThatThrownBy(() -> service.act(id,
                new ActionRequest(ActionRequest.Type.SUNDAY, null, null, null, SundayActivity.REST, null, null, null)))
                .isInstanceOf(InvalidActionException.class);
        assertThat(service.actions(id)).isEmpty();
    }

    @Test
    void replayFromLogMatchesLiveState() {
        UUID id = service.create(77L).runId();
        GameService.ActionResult last = null;
        for (int i = 0; i < 12; i++) {
            GameState s = service.load(id).state();
            ActionRequest r = switch (engine.phase(s)) {
                case EVENT -> new ActionRequest(ActionRequest.Type.EVENT_CHOICE, null, null, null, null, null,
                        s.pendingEvents.peekFirst().eventId(), 0);
                case SUNDAY -> new ActionRequest(ActionRequest.Type.SUNDAY, null, null, null, SundayActivity.PART_TIME, null, null, null);
                default -> day(null, null, Map.of());
            };
            last = service.act(id, r);
        }
        GameState replayed = service.replay(77L, repo.actions(id));
        assertThat(StateFingerprint.of(replayed)).isEqualTo(StateFingerprint.of(last.loaded().state()));
    }
}
