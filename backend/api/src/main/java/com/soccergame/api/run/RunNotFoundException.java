package com.soccergame.api.run;

import java.util.UUID;

public class RunNotFoundException extends RuntimeException {
    public RunNotFoundException(UUID id) {
        super("판을 찾을 수 없습니다: " + id);
    }
}
