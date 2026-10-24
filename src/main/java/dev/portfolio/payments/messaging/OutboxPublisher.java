package dev.portfolio.payments.messaging;

import dev.portfolio.payments.persistence.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final MeterRegistry metrics;
    private final String topic;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
                           TransactionTemplate transaction, MeterRegistry metrics,
                           @Value("${app.kafka.requested-topic}") String topic,
                           @Value("${app.outbox.batch-size:20}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transaction = transaction;
        this.metrics = metrics;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    public void publishPending() {
        try {
            transaction.executeWithoutResult(status -> {
                for (var message : outbox.lockPending(batchSize)) {
                    try {
                        // Broker acknowledges before DB marks event sent; a crash can cause replay.
                        kafka.send(topic, message.aggregateId().toString(), message.payload())
                                .get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException("Kafka publish failed: " + message.eventId(), e);
                    }
                    outbox.markPublished(message.eventId());
                    metrics.counter("payments.outbox.published").increment();
                }
            });
        } catch (Exception e) {
            // Transaction rolls back, leaving rows available for the next scheduled attempt.
            log.warn("Outbox publish attempt failed; will retry: {}", e.toString());
        }
    }
}
