package com.soccergame.domain.engine;

import com.soccergame.domain.match.MatchRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 상태 요약 해시. 같은 시드와 같은 행동 순서면 같은 값이 나와야 한다. */
public final class StateFingerprint {
    private StateFingerprint() {
    }

    public static String of(GameState s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.seed).append('|').append(s.week).append('|').append(s.day).append('|').append(s.actionCount)
                .append('|').append(s.rng.state()).append('|');
        s.stats.asMap().forEach((k, v) -> sb.append(k).append('=').append(Double.doubleToLongBits(v)).append(','));
        sb.append('|').append(Double.doubleToLongBits(s.stamina)).append('|').append(s.condition).append('|')
                .append(s.money).append('|').append(Double.doubleToLongBits(s.academics)).append('|')
                .append(Double.doubleToLongBits(s.reputation)).append('|').append(s.affinity).append('|')
                .append(s.injuredUntilDay).append('|').append(s.eventHistory).append('|').append(s.flags);
        for (MatchRecord m : s.matches) {
            sb.append('|').append(m.ourScore()).append(':').append(m.theirScore()).append(m.role())
                    .append(m.rating());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
