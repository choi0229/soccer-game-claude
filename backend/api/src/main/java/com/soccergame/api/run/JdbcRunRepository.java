package com.soccergame.api.run;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRunRepository implements RunRepository {
    private final JdbcClient jdbc;

    public JdbcRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createRun(UUID id, long seed) {
        jdbc.sql("insert into runs (id, seed) values (?, ?)").params(id, seed).update();
    }

    @Override
    public Optional<Long> lockRun(UUID id) {
        return jdbc.sql("select seed from runs where id = ? for update").param(id).query(Long.class).optional();
    }

    @Override
    public Optional<Long> findSeed(UUID id) {
        return jdbc.sql("select seed from runs where id = ?").param(id).query(Long.class).optional();
    }

    @Override
    public List<StoredAction> actions(UUID id) {
        return jdbc.sql("select seq, action_type, payload::text as payload from run_actions where run_id = ? order by seq")
                .param(id)
                .query((rs, n) -> new StoredAction(rs.getInt("seq"), rs.getString("action_type"),
                        rs.getString("payload")))
                .list();
    }

    @Override
    public void appendAction(UUID id, int seq, String type, String payload) {
        jdbc.sql("insert into run_actions (run_id, seq, action_type, payload) values (?, ?, ?, cast(? as jsonb))")
                .params(id, seq, type, payload)
                .update();
    }
}
