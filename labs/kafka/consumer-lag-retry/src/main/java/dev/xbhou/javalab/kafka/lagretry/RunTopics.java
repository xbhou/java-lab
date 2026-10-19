package dev.xbhou.javalab.kafka.lagretry;

import java.util.List;

public record RunTopics(
        String lag,
        String main,
        String retry,
        String dlq
) {

    public static RunTopics create() {
        String suffix =
                Long.toString(
                        System.currentTimeMillis()
                );

        return new RunTopics(
                "java-lab.lag." + suffix,
                "java-lab.orders." + suffix,
                "java-lab.orders.retry." + suffix,
                "java-lab.orders.dlq." + suffix
        );
    }

    public List<String> all() {
        return List.of(
                lag,
                main,
                retry,
                dlq
        );
    }
}
