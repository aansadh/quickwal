package com.quickwal.wallet.adapter.in;

import com.quickwal.common.architecture.InboundAdapter;
import com.quickwal.common.messages.MessageType;
import com.quickwal.common.messages.command.CreditCommand;
import com.quickwal.common.messages.command.DebitCommand;
import com.quickwal.common.messages.command.RefundCommand;
import com.quickwal.wallet.core.port.in.ProcessWalletCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.ObjectMapper;

@InboundAdapter
public class WalletCommandKafkaConsumer {
    private static final Logger log = LoggerFactory.getLogger(WalletCommandKafkaConsumer.class);

    private final ProcessWalletCommand processWalletCommand;
    private final ObjectMapper objectMapper;

    public WalletCommandKafkaConsumer(
            ProcessWalletCommand processWalletCommand,
            ObjectMapper objectMapper) {
        this.processWalletCommand = processWalletCommand;
        this.objectMapper = objectMapper;
    }

    /**
     * Deserializes a wallet command and delegates it to the wallet use case.
     *
     * @param payload serialized command received from Kafka
     */
    @KafkaListener(
            topics = "${quickwal.kafka.topics.wallet-commands}",
            groupId = "${quickwal.kafka.consumer.wallet-group-id}")
    public void consume(String payload) {
        MessageType eventType = null;
        try {
            eventType = MessageType.fromWireName(readEnvelope(payload).eventType());
            switch (eventType) {
                case DEBIT_COMMAND -> processWalletCommand.debit(
                        deserialize(payload, DebitCommand.class));
                case CREDIT_COMMAND -> processWalletCommand.credit(
                        deserialize(payload, CreditCommand.class));
                case REFUND_COMMAND -> processWalletCommand.refund(
                        deserialize(payload, RefundCommand.class));
                default -> throw new IllegalArgumentException(
                        "Unsupported wallet command type: " + eventType.wireName());
            }
            log.debug("Processed wallet command: commandType={}", eventType.wireName());
        } catch (IllegalArgumentException exception) {
            log.warn("Wallet command rejected: commandType={}, reason={}",
                    eventType == null ? "unknown" : eventType.wireName(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Failed to process wallet command: commandType={}",
                    eventType == null ? "unknown" : eventType.wireName(), exception);
            throw exception;
        }
    }

    private EventEnvelope readEnvelope(String payload) {
        try {
            EventEnvelope envelope = objectMapper.readValue(payload, EventEnvelope.class);
            if (envelope == null || envelope.eventType() == null || envelope.eventType().isBlank()) {
                throw new IllegalArgumentException("Kafka payload is missing eventType");
            }
            return envelope;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to determine wallet command type", e);
        }
    }

    private <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to deserialize " + type.getSimpleName(), e);
        }
    }

    private record EventEnvelope(String eventType) {
    }
}
