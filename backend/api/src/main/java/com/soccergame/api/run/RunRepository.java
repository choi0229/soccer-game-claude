package com.soccergame.api.run;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 판과 행동 기록 저장소. 행동 기록은 추가만 한다. */
public interface RunRepository {

    record StoredAction(int seq, String type, String payload) {
    }

    void createRun(UUID id, long seed);

    /** 판 행을 잠그고 시드를 돌려준다 (같은 판의 동시 요청을 한 줄로 세운다). */
    Optional<Long> lockRun(UUID id);

    Optional<Long> findSeed(UUID id);

    List<StoredAction> actions(UUID id);

    void appendAction(UUID id, int seq, String type, String payload);
}
