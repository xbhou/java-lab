package dev.xbhou.javalab.boot.app;

import dev.xbhou.javalab.boot.autoconfigure.DemoGreetingAutoConfiguration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.ConditionAndOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.ConditionAndOutcomes;
import org.springframework.context.ConfigurableApplicationContext;

public final class ConditionReportPrinter {

    private ConditionReportPrinter() {
    }

    public static void print(
            ConfigurableApplicationContext context
    ) {
        ConditionEvaluationReport report =
                ConditionEvaluationReport.get(
                        context.getBeanFactory()
                );

        String target =
                DemoGreetingAutoConfiguration.class.getName();

        System.out.println(
                "--- ConditionEvaluationReport ---"
        );

        report.getConditionAndOutcomesBySource()
                .entrySet()
                .stream()
                .filter(entry ->
                        entry.getKey().contains(target)
                )
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        ConditionReportPrinter::printEntry
                );
    }

    private static void printEntry(
            Map.Entry<String, ConditionAndOutcomes> entry
    ) {
        System.out.println("source=" + entry.getKey());

        for (ConditionAndOutcome conditionAndOutcome
                : entry.getValue()) {

            var outcome =
                    conditionAndOutcome.getOutcome();

            System.out.printf(
                    "  %-8s %s -> %s%n",
                    outcome.isMatch()
                            ? "MATCH"
                            : "NO MATCH",
                    conditionAndOutcome
                            .getCondition()
                            .getClass()
                            .getSimpleName(),
                    outcome.getMessage()
            );
        }
    }
}
