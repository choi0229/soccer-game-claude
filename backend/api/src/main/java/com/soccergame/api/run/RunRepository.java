package com.soccergame.api.run;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 판과 행동 기록 저장소. 행동 기록은 추가만 한다. */
public interface RunRepository {

    record StoredAction(int seq, String type, String payload) {
    }

    /** 판 생성 기록: 시드와 고른 학교 (null 이면 기본 학교) */
    record RunInfo(long seed, Integer schoolId) {
    }

    void createRun(UUID id, long seed, Integer schoolId);

    /** 판 행을 잠그고 생성 기록을 돌려준다 (같은 판의 동시 요청을 한 줄로 세운다). */
    Optional<RunInfo> lockRun(UUID id);

    Optional<RunInfo> findRun(UUID id);

    List<StoredAction> actions(UUID id);

    void appendAction(UUID id, int seq, String type, String payload);
}
