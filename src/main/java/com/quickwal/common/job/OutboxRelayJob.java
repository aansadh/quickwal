package com.quickwal.common.job;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxRelayJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final JdbcClient jdbcClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String walletCommandsTopic;
    private final String transferEventsTopic;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxRelayJob(
            JdbcClient jdbcClient,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${quickwal.kafka.topics.wallet-commands}") String walletCommandsTopic,
            @Value("${quickwal.kafka.topics.transfer-events}") String transferEventsTopic,
            @Value("${quickwal.outbox.batch-size}") int batchSize,
            @Value("${quickwal.outbox.send-timeout-ms}") long sendTimeoutMs) {
        this.jdbcClient = jdbcClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.walletCommandsTopic = walletCommandsTopic;
        this.transferEventsTopic = transferEventsTopic;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${quickwal.outbox.poll-delay-ms}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxRow> rows = jdbcClient.sql("""
                SELECT id, aggregate_type, aggregate_id, event_type, payload::text AS payload
                FROM outbox_events
                ORDER BY created_at, id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """)
                .param("batchSize", batchSize)
                .query(this::mapRow)
                .list();

        if (rows.isEmpty()) {
            return;
        }

        List<CompletableFuture<SendResult<String, String>>> sendFutures = new ArrayList<>(rows.size());
        for (OutboxRow row : rows) {
            String topic = resolveTopic(row.aggregateType());
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic,
                    null,
                    resolveMessageKey(row),
                    row.payload(),
                    new org.apache.kafka.common.header.internals.RecordHeaders()
                            .add("eventType", row.eventType().getBytes(StandardCharsets.UTF_8)));
            try {
                CompletableFuture<SendResult<String, String>> sendFuture = kafkaTemplate.send(record);
                sendFutures.add(sendFuture.whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish outbox event: eventId={}, aggregateType={}, "
                                        + "aggregateId={}, topic={}",
                                row.id(), row.aggregateType(), row.aggregateId(), topic, error);
                    }
                }));
            } catch (RuntimeException e) {
                log.error("Could not enqueue outbox event for Kafka send: eventId={}, "
                                + "aggregateType={}, aggregateId={}, topic={}",
                        row.id(), row.aggregateType(), row.aggregateId(), topic, e);
                throw new IllegalStateException(
                        "Could not enqueue outbox event " + row.id(), e);
            }
        }

        try {
            CompletableFuture.allOf(sendFutures.toArray(CompletableFuture[]::new))
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for outbox batch publication: batchSize={}",
                    rows.size(), e);
            throw new IllegalStateException("Interrupted while publishing outbox batch", e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to publish outbox batch: batchSize={}, timeoutMs={}",
                    rows.size(), sendTimeoutMs, e);
            throw new IllegalStateException("Failed to publish outbox batch", e);
        }

        for (OutboxRow row : rows) {
            int deleted = jdbcClient.sql("DELETE FROM outbox_events WHERE id = :id")
                    .param("id", row.id())
                    .update();
            if (deleted != 1) {
                log.error("Published outbox event could not be removed: eventId={}, deletedRows={}",
                        row.id(), deleted);
                throw new IllegalStateException(
                        "Could not delete published outbox event " + row.id());
            }
        }

        log.debug("Published and removed {} outbox events", rows.size());
    }

    private String resolveTopic(String aggregateType) {
        return switch (aggregateType) {
            case "TRANSFER" -> walletCommandsTopic;
            case "WALLET" -> transferEventsTopic;
            default -> {
                log.error("Outbox event has unsupported aggregate type: aggregateType={}", aggregateType);
                throw new IllegalStateException("Unsupported outbox aggregate type: " + aggregateType);
            }
        };
    }

    private String resolveMessageKey(OutboxRow row) {
        if ("WALLET".equals(row.aggregateType())) {
            return row.aggregateId().toString();
        }
        if (!"TRANSFER".equals(row.aggregateType())) {
            throw new IllegalStateException(
                    "Unsupported outbox aggregate type: " + row.aggregateType());
        }

        try {
            JsonNode command = objectMapper.readTree(row.payload());
            for (String field : List.of("sourceWalletId", "targetWalletId")) {
                JsonNode walletId = command.get(field);
                if (walletId != null && !walletId.isNull() && !walletId.asText().isBlank()) {
                    return walletId.asText();
                }
            }
            throw new IllegalStateException(
                    "Wallet command has no sourceWalletId or targetWalletId: eventId=" + row.id());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to resolve wallet command partition key for event " + row.id(), e);
        }
    }

    private OutboxRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxRow(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("payload"));
    }

    private record OutboxRow(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload) {
    }
}
