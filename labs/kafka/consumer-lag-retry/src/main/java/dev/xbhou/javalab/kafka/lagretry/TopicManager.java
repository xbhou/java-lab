package dev.xbhou.javalab.kafka.lagretry;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;

public class TopicManager implements AutoCloseable {

    private final Admin admin;

    public TopicManager() {
        this.admin = Admin.create(
                KafkaLabConfig.adminProperties()
        );
    }

    public void createTopics(List<String> names)
            throws Exception {
        List<NewTopic> topics =
                names.stream()
                        .map(name ->
                                new NewTopic(
                                        name,
                                        1,
                                        (short) 1
                                )
                        )
                        .toList();

        admin.createTopics(topics)
                .all()
                .get(15, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        admin.close();
    }
}
