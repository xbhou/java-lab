package dev.xbhou.javalab.kafka.lagretry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

public class RetryScenario {

    private static final Duration POLL_TIMEOUT =
            Duration.ofSeconds(1);

    private static final int MAX_ATTEMPTS = 3;

    public void run(
            RunTopics topics,
            String runId
    ) throws Exception {
        System.out.println();
        System.out.println(
                "=== Scenario 2: retry topic + DLQ ==="
        );

        String mainGroup =
                "java-lab-main-" + runId;

        String retryGroup =
                "java-lab-retry-" + runId;

        List<String> processedOrder =
                new ArrayList<>();

        DemoMessageProcessor processor =
                new DemoMessageProcessor(
                        processedOrder
                );

        try (KafkaProducer<String, String> producer =
                     new KafkaProducer<>(
                             KafkaLabConfig
                                     .producerProperties()
                     )) {

            produceOriginalMessages(
                    producer,
                    topics.main()
            );

            consumeMainTopic(
                    producer,
                    processor,
                    topics,
                    mainGroup
            );

            consumeRetryTopic(
                    producer,
                    processor,
                    topics,
                    retryGroup,
                    processedOrder
            );

            verifyDlq(
                    topics.dlq(),
                    runId
            );
        }

        List<String> expectedOrder =
                List.of(
                        "event-1",
                        "event-3",
                        "event-2"
                );

        if (!expectedOrder.equals(processedOrder)) {
            throw new IllegalStateException(
                    "retry-topic ordering demo mismatch "
                            + "expected="
                            + expectedOrder
                            + " actual="
                            + processedOrder
            );
        }

        System.out.println(
                "processed order=" + processedOrder
        );

        System.out.println(
                "ordering trade-off verified: "
                        + "event-3 completed before retried event-2"
        );
    }

    private static void produceOriginalMessages(
            KafkaProducer<String, String> producer,
            String topic
    ) throws Exception {
        List<String> values =
                List.of(
                        "OK:event-1",
                        "TRANSIENT:event-2",
                        "OK:event-3",
                        "POISON:event-4"
                );

        for (String value : values) {
            producer.send(
                    new ProducerRecord<>(
                            topic,
                            0,
                            "order-1",
                            value
                    )
            ).get();
        }

        producer.flush();
    }

    private static void consumeMainTopic(
            KafkaProducer<String, String> producer,
            DemoMessageProcessor processor,
            RunTopics topics,
            String groupId
    ) throws Exception {
        TopicPartition partition =
                new TopicPartition(
                        topics.main(),
                        0
                );

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(
                             KafkaLabConfig
                                     .consumerProperties(
                                             groupId
                                     )
                     )) {

            consumer.assign(List.of(partition));
            consumer.seek(partition, 0L);

            int consumed = 0;

            while (consumed < 4) {
                ConsumerRecords<String, String> records =
                        consumer.poll(POLL_TIMEOUT);

                for (ConsumerRecord<String, String> record
                        : records) {

                    try {
                        processor.process(
                                record.value(),
                                0
                        );
                    } catch (
                            DemoMessageProcessor
                                    .ProcessingException error
                    ) {
                        publishRetry(
                                producer,
                                topics.retry(),
                                record,
                                1
                        );
                    }

                    commitRecord(
                            consumer,
                            partition,
                            record
                    );

                    consumed++;
                }
            }
        }
    }

    private static void consumeRetryTopic(
            KafkaProducer<String, String> producer,
            DemoMessageProcessor processor,
            RunTopics topics,
            String groupId,
            List<String> processedOrder
    ) throws Exception {
        TopicPartition partition =
                new TopicPartition(
                        topics.retry(),
                        0
                );

        boolean dlqSent = false;

        long deadline =
                System.nanoTime()
                        + Duration.ofSeconds(20)
                        .toNanos();

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(
                             KafkaLabConfig
                                     .consumerProperties(
                                             groupId
                                     )
                     )) {

            consumer.assign(List.of(partition));
            consumer.seek(partition, 0L);

            while (
                    !(processedOrder.contains("event-2")
                            && dlqSent)
            ) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException(
                            "timed out draining retry topic"
                    );
                }

                ConsumerRecords<String, String> records =
                        consumer.poll(POLL_TIMEOUT);

                for (ConsumerRecord<String, String> record
                        : records) {

                    int attempt =
                            readAttempt(record);

                    sleep(backoffMillis(attempt));

                    try {
                        processor.process(
                                record.value(),
                                attempt
                        );
                    } catch (
                            DemoMessageProcessor
                                    .ProcessingException error
                    ) {
                        if (attempt < MAX_ATTEMPTS) {
                            publishRetry(
                                    producer,
                                    topics.retry(),
                                    record,
                                    attempt + 1
                            );
                        } else {
                            publishDlq(
                                    producer,
                                    topics.dlq(),
                                    record,
                                    attempt,
                                    error
                            );

                            dlqSent = true;
                        }
                    }

                    commitRecord(
                            consumer,
                            partition,
                            record
                    );
                }
            }
        }
    }

    private static void publishRetry(
            KafkaProducer<String, String> producer,
            String retryTopic,
            ConsumerRecord<String, String> source,
            int attempt
    ) throws Exception {
        ProducerRecord<String, String> retry =
                new ProducerRecord<>(
                        retryTopic,
                        0,
                        source.key(),
                        source.value()
                );

        copyOriginHeaders(
                retry,
                source
        );

        retry.headers().add(
                new RecordHeader(
                        "retry-attempt",
                        Integer.toString(attempt)
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                )
        );

        producer.send(retry).get();

        System.out.println(
                "retry publish value="
                        + source.value()
                        + " attempt="
                        + attempt
        );
    }

    private static void publishDlq(
            KafkaProducer<String, String> producer,
            String dlqTopic,
            ConsumerRecord<String, String> source,
            int attempt,
            Exception error
    ) throws Exception {
        ProducerRecord<String, String> dlq =
                new ProducerRecord<>(
                        dlqTopic,
                        0,
                        source.key(),
                        source.value()
                );

        copyOriginHeaders(
                dlq,
                source
        );

        dlq.headers().add(
                new RecordHeader(
                        "final-attempt",
                        Integer.toString(attempt)
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                )
        );

        dlq.headers().add(
                new RecordHeader(
                        "error",
                        error.getMessage()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                )
        );

        producer.send(dlq).get();

        System.out.println(
                "DLQ publish value="
                        + source.value()
                        + " finalAttempt="
                        + attempt
        );
    }

    private static void copyOriginHeaders(
            ProducerRecord<String, String> target,
            ConsumerRecord<String, String> source
    ) {
        Header originalTopic =
                source.headers()
                        .lastHeader(
                                "original-topic"
                        );

        Header originalOffset =
                source.headers()
                        .lastHeader(
                                "original-offset"
                        );

        if (originalTopic == null) {
            target.headers().add(
                    new RecordHeader(
                            "original-topic",
                            source.topic()
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    )
            );
        } else {
            target.headers().add(
                    new RecordHeader(
                            "original-topic",
                            originalTopic.value()
                    )
            );
        }

        if (originalOffset == null) {
            target.headers().add(
                    new RecordHeader(
                            "original-offset",
                            Long.toString(
                                    source.offset()
                            ).getBytes(
                                    StandardCharsets.UTF_8
                            )
                    )
            );
        } else {
            target.headers().add(
                    new RecordHeader(
                            "original-offset",
                            originalOffset.value()
                    )
            );
        }
    }

    private static int readAttempt(
            ConsumerRecord<String, String> record
    ) {
        Header header =
                record.headers()
                        .lastHeader(
                                "retry-attempt"
                        );

        if (header == null) {
            return 0;
        }

        return Integer.parseInt(
                new String(
                        header.value(),
                        StandardCharsets.UTF_8
                )
        );
    }

    private static void verifyDlq(
            String dlqTopic,
            String runId
    ) {
        TopicPartition partition =
                new TopicPartition(
                        dlqTopic,
                        0
                );

        String groupId =
                "java-lab-dlq-inspect-" + runId;

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(
                             KafkaLabConfig
                                     .consumerProperties(
                                             groupId
                                     )
                     )) {

            consumer.assign(List.of(partition));
            consumer.seek(partition, 0L);

            long deadline =
                    System.nanoTime()
                            + Duration.ofSeconds(10)
                            .toNanos();

            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records =
                        consumer.poll(POLL_TIMEOUT);

                for (ConsumerRecord<String, String> record
                        : records) {

                    if (!"POISON:event-4"
                            .equals(record.value())) {
                        throw new IllegalStateException(
                                "unexpected DLQ value="
                                        + record.value()
                        );
                    }

                    int attempt =
                            Integer.parseInt(
                                    headerValue(
                                            record,
                                            "final-attempt"
                                    )
                            );

                    if (attempt != MAX_ATTEMPTS) {
                        throw new IllegalStateException(
                                "expected final attempt "
                                        + MAX_ATTEMPTS
                                        + " but got "
                                        + attempt
                        );
                    }

                    System.out.println(
                            "DLQ verified value="
                                    + record.value()
                                    + " finalAttempt="
                                    + attempt
                    );

                    return;
                }
            }
        }

        throw new IllegalStateException(
                "expected poison message in DLQ"
        );
    }

    private static String headerValue(
            ConsumerRecord<String, String> record,
            String name
    ) {
        Header header =
                record.headers()
                        .lastHeader(name);

        if (header == null) {
            throw new IllegalStateException(
                    "missing header " + name
            );
        }

        return new String(
                header.value(),
                StandardCharsets.UTF_8
        );
    }

    private static void commitRecord(
            KafkaConsumer<String, String> consumer,
            TopicPartition partition,
            ConsumerRecord<String, String> record
    ) {
        consumer.commitSync(
                Map.of(
                        partition,
                        new OffsetAndMetadata(
                                record.offset() + 1
                        )
                )
        );
    }

    private static long backoffMillis(
            int attempt
    ) {
        return 100L
                * (1L << Math.max(0, attempt - 1));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "retry backoff interrupted",
                    error
            );
        }
    }
}
