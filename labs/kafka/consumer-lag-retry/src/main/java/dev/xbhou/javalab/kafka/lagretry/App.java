package dev.xbhou.javalab.kafka.lagretry;

public class App {

    public static void main(String[] args)
            throws Exception {
        RunTopics topics =
                RunTopics.create();

        String runId =
                Long.toString(
                        System.currentTimeMillis()
                );

        try (TopicManager topicManager =
                     new TopicManager()) {

            topicManager.createTopics(
                    topics.all()
            );
        }

        String lagGroup =
                "java-lab-lag-" + runId;

        new LagScenario().run(
                topics.lag(),
                lagGroup
        );

        new RetryScenario().run(
                topics,
                runId
        );

        System.out.println();
        System.out.println(
                "=== Inspect after the run ==="
        );

        System.out.println(
                "lag topic="
                        + topics.lag()
        );

        System.out.println(
                "lag group="
                        + lagGroup
        );

        System.out.println(
                "main topic="
                        + topics.main()
        );

        System.out.println(
                "retry topic="
                        + topics.retry()
        );

        System.out.println(
                "dlq topic="
                        + topics.dlq()
        );

        System.out.println();
        System.out.println(
                "All Kafka lag/retry scenarios verified."
        );
    }
}
