package com.quickwal.transfer.adapter.in.messaging;

import com.quickwal.common.architecture.InboundAdapter;
import com.quickwal.common.messages.MessageType;
import com.quickwal.common.messages.event.CreditCompletedEvent;
import com.quickwal.common.messages.event.CreditFailedEvent;
import com.quickwal.common.messages.event.DebitCompletedEvent;
import com.quickwal.common.messages.event.DebitFailedEvent;
import com.quickwal.common.messages.event.RefundCompletedEvent;
import com.quickwal.transfer.core.port.in.AdvanceTransferSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.ObjectMapper;

@InboundAdapter
public class TransferEventKafkaConsumer {
    private static final Logger log = LoggerFactory.getLogger(TransferEventKafkaConsumer.class);

    private final AdvanceTransferSaga advanceTransferSaga;
    private final ObjectMapper objectMapper;

    public TransferEventKafkaConsumer(AdvanceTransferSaga advanceTransferSaga, ObjectMapper objectMapper) {
        this.advanceTransferSaga = advanceTransferSaga;
        this.objectMapper = objectMapper;
    }

    /**
     * Deserializes a transfer event and delegates it to the transfer saga use case.
     *
     * @param payload serialized event received from Kafka
     */
    @KafkaListener(topics = "${quickwal.kafka.topics.transfer-events}",
            groupId = "${quickwal.kafka.consumer.transfer-group-id}")
    public void consume(String payload) {
        MessageType eventType = null;
        try {
            eventType = MessageType.fromWireName(readEventType(payload));
            switch (eventType) {
                case DEBIT_COMPLETED_EVENT -> advanceTransferSaga.handleDebitCompleted(read(payload, DebitCompletedEvent.class));
                case DEBIT_FAILED_EVENT -> advanceTransferSaga.handleDebitFailed(read(payload, DebitFailedEvent.class));
                case CREDIT_COMPLETED_EVENT -> advanceTransferSaga.handleCreditCompleted(read(payload, CreditCompletedEvent.class));
                case CREDIT_FAILED_EVENT -> advanceTransferSaga.handleCreditFailed(read(payload, CreditFailedEvent.class));
                case REFUND_COMPLETED_EVENT -> advanceTransferSaga.handleRefundCompleted(read(payload, RefundCompletedEvent.class));
                default -> throw new IllegalArgumentException("Unsupported transfer event type: " + eventType.wireName());
            }
            log.debug("Processed transfer event: eventType={}", eventType.wireName());
        } catch (IllegalArgumentException exception) {
            log.warn("Transfer event rejected: eventType={}, reason={}",
                    eventType == null ? "unknown" : eventType.wireName(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Failed to process transfer event: eventType={}",
                    eventType == null ? "unknown" : eventType.wireName(), exception);
            throw exception;
        }
    }

    private String readEventType(String payload) {
        try {
            EventEnvelope envelope = objectMapper.readValue(payload, EventEnvelope.class);
            if (envelope == null || envelope.eventType() == null || envelope.eventType().isBlank()) {
                throw new IllegalArgumentException("Kafka payload is missing eventType");
            }
            return envelope.eventType();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to determine transfer event type", e);
        }
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            T event = objectMapper.readValue(payload, type);
            if (event == null) throw new IllegalArgumentException("Kafka payload is empty");
            return event;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to deserialize " + type.getSimpleName(), e);
        }
    }

    private record EventEnvelope(String eventType) { }
}

