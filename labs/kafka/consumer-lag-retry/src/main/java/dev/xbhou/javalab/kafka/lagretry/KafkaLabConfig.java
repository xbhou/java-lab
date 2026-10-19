package dev.xbhou.javalab.kafka.lagretry;

import java.util.Properties;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaLabConfig {

    public static final String BOOTSTRAP_SERVERS =
            "localhost:9092";

    private KafkaLabConfig() {
    }

    public static Properties adminProperties() {
        Properties properties = new Properties();

        properties.put(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                BOOTSTRAP_SERVERS
        );

        return properties;
    }

    public static Properties producerProperties() {
        Properties properties = new Properties();

        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                BOOTSTRAP_SERVERS
        );

        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        properties.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );

        return properties;
    }

    public static Properties consumerProperties(
            String groupId
    ) {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                BOOTSTRAP_SERVERS
        );

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                groupId
        );

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                "false"
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        properties.put(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                "1"
        );

        return properties;
    }
}
