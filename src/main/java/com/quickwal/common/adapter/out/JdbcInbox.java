package com.quickwal.common.adapter.out;

import com.quickwal.common.architecture.OutboundAdapter;
import com.quickwal.common.events.MessageConsumer;
import com.quickwal.common.port.out.Inbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.UUID;

@OutboundAdapter
public class JdbcInbox implements Inbox {
    private final JdbcClient jdbcClient;

    public JdbcInbox(JdbcClient jdbcClient) { this.jdbcClient = jdbcClient; }

    @Override
    public boolean markIfNew(UUID eventId, MessageConsumer consumer) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO inbox_events (event_id, consumer_name)
                        VALUES (:eventId, :consumerName)
                        ON CONFLICT (event_id, consumer_name) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("consumerName", consumer.code())
                .update();
        return inserted == 1;
    }
}
