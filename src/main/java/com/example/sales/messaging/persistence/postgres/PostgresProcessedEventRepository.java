package com.example.sales.messaging.persistence.postgres;

import com.example.sales.common.config.PostgresAdapter;
import com.example.sales.messaging.domain.ProcessedEventRepository;
import com.example.sales.messaging.domain.SalesEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

@PostgresAdapter
class PostgresProcessedEventRepository implements ProcessedEventRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    PostgresProcessedEventRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean markProcessed(SalesEvent event) {
        return jdbc.sql("""
                        insert into processed_events (event_id, event_type, payload)
                        values (:eventId, :eventType, cast(:payload as jsonb))
                        on conflict (event_id) do nothing""")
                .param("eventId", event.eventId())
                .param("eventType", event.getClass().getSimpleName())
                .param("payload", json.writeValueAsString(event))
                .update() > 0;
    }
}
