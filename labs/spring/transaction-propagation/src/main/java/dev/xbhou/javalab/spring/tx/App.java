package dev.xbhou.javalab.spring.tx;

import java.util.List;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.UnexpectedRollbackException;

public class App {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             TransactionConfig.class
                     )) {

            LedgerRepository repository =
                    context.getBean(LedgerRepository.class);

            OuterService outer =
                    context.getBean(OuterService.class);

            SelfInvocationService self =
                    context.getBean(
                            SelfInvocationService.class
                    );

            repository.initialize();

            scenarioRequired(repository, outer);
            scenarioRequiresNew(repository, outer);
            scenarioNested(repository, outer);
            scenarioSelfInvocation(repository, self);
            scenarioExternalRequiresNew(repository, self);

            System.out.println();
            System.out.println(
                    "All transaction propagation scenarios verified."
            );
        }
    }

    private static void scenarioRequired(
            LedgerRepository repository,
            OuterService outer
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 1: REQUIRED + caught inner failure ==="
        );

        repository.reset();

        try {
            outer.requiredInnerFailureCaught();

            throw new IllegalStateException(
                    "Expected UnexpectedRollbackException"
            );
        } catch (UnexpectedRollbackException expected) {
            System.out.println(
                    "caught UnexpectedRollbackException as expected"
            );
        }

        verifyRows(
                repository,
                "REQUIRED",
                List.of()
        );
    }

    private static void scenarioRequiresNew(
            LedgerRepository repository,
            OuterService outer
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 2: REQUIRES_NEW survives outer rollback ==="
        );

        repository.reset();

        try {
            outer.requiresNewThenOuterFails();
        } catch (IllegalStateException expected) {
            System.out.println(
                    "outer failed as expected"
            );
        }

        verifyRows(
                repository,
                "REQUIRES_NEW",
                List.of("inner-requires-new")
        );
    }

    private static void scenarioNested(
            LedgerRepository repository,
            OuterService outer
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 3: NESTED rollback to savepoint ==="
        );

        repository.reset();

        outer.nestedFailureCaught();

        verifyRows(
                repository,
                "NESTED",
                List.of(
                        "outer-nested-before",
                        "outer-nested-after"
                )
        );
    }

    private static void scenarioSelfInvocation(
            LedgerRepository repository,
            SelfInvocationService self
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 4: self-invocation bypasses REQUIRES_NEW ==="
        );

        repository.reset();

        try {
            self.outerCallsRequiresNewOnThisThenFails();
        } catch (IllegalStateException expected) {
            System.out.println(
                    "outer failed as expected"
            );
        }

        verifyRows(
                repository,
                "self-invocation",
                List.of()
        );
    }

    private static void scenarioExternalRequiresNew(
            LedgerRepository repository,
            SelfInvocationService self
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 5: external REQUIRES_NEW call commits ==="
        );

        repository.reset();

        self.innerRequiresNew();

        verifyRows(
                repository,
                "external REQUIRES_NEW",
                List.of("self-inner")
        );
    }

    private static void verifyRows(
            LedgerRepository repository,
            String scenario,
            List<String> expected
    ) {
        List<String> actual = repository.labels();

        System.out.println(
                "committed rows=" + actual
        );

        ScenarioVerifier.verify(
                scenario,
                expected,
                actual
        );
    }
}
