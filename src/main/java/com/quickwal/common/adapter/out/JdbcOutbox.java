package com.quickwal.common.adapter.out;

import com.quickwal.common.architecture.OutboundAdapter;
import com.quickwal.common.events.AggregateType;
import com.quickwal.common.messages.Message;
import com.quickwal.common.port.out.Outbox;
import com.quickwal.common.utils.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@OutboundAdapter
public class JdbcOutbox implements Outbox {
    private static final Logger log = LoggerFactory.getLogger(JdbcOutbox.class);

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcOutbox(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AggregateType aggregateType, UUID aggregateId, Message message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize outbox message: aggregateType={}, aggregateId={}, eventType={}",
                    aggregateType.code(), aggregateId, message.getEventType(), e);
            throw new IllegalStateException("Failed to serialize outbox message", e);
        }

        jdbcClient.sql("""
                        INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload)
                        VALUES (:id, :type, :aggregateId, :eventType, CAST(:payload AS jsonb))
                        """)
                .param("id", IdGenerator.generate())
                .param("type", aggregateType.code())
                .param("aggregateId", aggregateId)
                .param("eventType", message.getEventType())
                .param("payload", payload)
                .update();
    }
}
