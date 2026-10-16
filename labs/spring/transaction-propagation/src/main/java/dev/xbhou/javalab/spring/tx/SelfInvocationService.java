package dev.xbhou.javalab.spring.tx;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class SelfInvocationService {

    private final LedgerRepository repository;
    private final TransactionInspector inspector;

    public SelfInvocationService(
            LedgerRepository repository,
            TransactionInspector inspector
    ) {
        this.repository = repository;
        this.inspector = inspector;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void outerCallsRequiresNewOnThisThenFails() {
        inspector.print("self outer REQUIRED");
        repository.insert("self-outer");

        this.innerRequiresNew();

        throw new IllegalStateException(
                "simulated self-invocation outer failure"
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void innerRequiresNew() {
        inspector.print("self inner REQUIRES_NEW");
        repository.insert("self-inner");
    }
}
