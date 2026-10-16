package dev.xbhou.javalab.spring.tx;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class OuterService {

    private final LedgerRepository repository;
    private final InnerService innerService;
    private final TransactionInspector inspector;

    public OuterService(
            LedgerRepository repository,
            InnerService innerService,
            TransactionInspector inspector
    ) {
        this.repository = repository;
        this.innerService = innerService;
        this.inspector = inspector;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void requiredInnerFailureCaught() {
        inspector.print("outer REQUIRED before");
        repository.insert("outer-required-before");

        try {
            innerService.requiredThenFail();
        } catch (IllegalStateException error) {
            System.out.println(
                    "outer caught inner REQUIRED failure"
            );
        }

        inspector.print("outer REQUIRED after");
        repository.insert("outer-required-after");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void requiresNewThenOuterFails() {
        inspector.print("outer before REQUIRES_NEW");
        repository.insert("outer-new-before");

        innerService.requiresNewCommit();

        inspector.print("outer after REQUIRES_NEW");
        repository.insert("outer-new-after");

        throw new IllegalStateException(
                "simulated outer failure"
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void nestedFailureCaught() {
        inspector.print("outer before NESTED");
        repository.insert("outer-nested-before");

        try {
            innerService.nestedThenFail();
        } catch (IllegalStateException error) {
            System.out.println(
                    "outer caught inner NESTED failure"
            );
        }

        inspector.print("outer after NESTED");
        repository.insert("outer-nested-after");
    }
}
