package com.soccergame.domain.engine;

/** 지금 받을 수 없는 행동. 상태는 바뀌지 않는다. */
public class InvalidActionException extends RuntimeException {
    public InvalidActionException(String message) {
        super(message);
    }
}
