package com.example.offer.publishing;

import com.example.offer.tools.JsonConfiguration;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

@Repository
@AllArgsConstructor
class OutboxRepository implements Outbox {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public boolean append(IntegrationEvent event) {
        int inserted = jdbc.update("""
                        insert into outbox (outbox_key, event_type, partition_key, payload, created_at)
                        values (?, ?, ?, cast(? as jsonb), ?)
                        on conflict (outbox_key) do nothing
                        """,
                event.outboxKey(), event.typeName(), event.productId(),
                json(event), Timestamp.from(clock.instant()));
        return inserted > 0;
    }

    List<OutboxMessage> pending(int limit) {
        return jdbc.query("""
                        select id, event_type, partition_key, payload::text as payload
                        from outbox
                        where published_at is null
                        order by id
                        limit ?
                        """,
                (rs, rowNum) -> new OutboxMessage(rs.getLong("id"), rs.getString("event_type"),
                        rs.getString("partition_key"), rs.getString("payload")),
                limit);
    }

    void markPublished(long id) {
        jdbc.update("update outbox set published_at = ? where id = ?", Timestamp.from(clock.instant()), id);
    }

    static String json(IntegrationEvent event) {
        try {
            return JsonConfiguration.OBJECT_MAPPER.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("cannot serialize outbox event " + event.typeName(), e);
        }
    }
}
