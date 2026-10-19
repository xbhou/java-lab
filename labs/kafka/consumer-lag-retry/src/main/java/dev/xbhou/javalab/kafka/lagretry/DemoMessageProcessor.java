package dev.xbhou.javalab.kafka.lagretry;

import java.util.List;

public class DemoMessageProcessor {

    private final List<String> processedOrder;

    public DemoMessageProcessor(
            List<String> processedOrder
    ) {
        this.processedOrder = processedOrder;
    }

    public void process(
            String value,
            int attempt
    ) {
        String[] parts =
                value.split(":", 2);

        String type = parts[0];
        String event = parts[1];

        switch (type) {
            case "OK" -> processedOrder.add(event);

            case "TRANSIENT" -> {
                if (attempt == 0) {
                    throw new ProcessingException(
                            "transient failure on first attempt"
                    );
                }

                processedOrder.add(event);
            }

            case "POISON" ->
                    throw new ProcessingException(
                            "poison message always fails"
                    );

            default ->
                    throw new IllegalArgumentException(
                            "unknown event type: " + type
                    );
        }
    }

    public static class ProcessingException
            extends RuntimeException {

        public ProcessingException(String message) {
            super(message);
        }
    }
}
