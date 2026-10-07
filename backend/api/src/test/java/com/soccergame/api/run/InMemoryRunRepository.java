package com.soccergame.api.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트용 저장소. 추가 전용 규칙(순번 연속, 중복 금지)을 그대로 지킨다. */
class InMemoryRunRepository implements RunRepository {
    final Map<UUID, RunInfo> runs = new HashMap<>();
    final Map<UUID, List<StoredAction>> actions = new HashMap<>();

    @Override
    public void createRun(UUID id, long seed, Integer schoolId) {
        runs.put(id, new RunInfo(seed, schoolId));
        actions.put(id, new ArrayList<>());
    }

    @Override
    public Optional<RunInfo> lockRun(UUID id) {
        return findRun(id);
    }

    @Override
    public Optional<RunInfo> findRun(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public List<StoredAction> actions(UUID id) {
        return List.copyOf(actions.get(id));
    }

    @Override
    public void appendAction(UUID id, int seq, String type, String payload) {
        List<StoredAction> list = actions.get(id);
        if (seq != list.size() + 1) {
            throw new IllegalStateException("순번 충돌: " + seq);
        }
        list.add(new StoredAction(seq, type, payload));
    }
}
