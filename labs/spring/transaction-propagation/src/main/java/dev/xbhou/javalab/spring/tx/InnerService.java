package dev.xbhou.javalab.spring.tx;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class InnerService {

    private final LedgerRepository repository;
    private final TransactionInspector inspector;

    public InnerService(
            LedgerRepository repository,
            TransactionInspector inspector
    ) {
        this.repository = repository;
        this.inspector = inspector;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void requiredThenFail() {
        inspector.print("inner REQUIRED");
        repository.insert("inner-required");

        throw new IllegalStateException(
                "simulated REQUIRED inner failure"
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void requiresNewCommit() {
        inspector.print("inner REQUIRES_NEW");
        repository.insert("inner-requires-new");
    }

    @Transactional(propagation = Propagation.NESTED)
    public void nestedThenFail() {
        inspector.print("inner NESTED");
        repository.insert("inner-nested");

        throw new IllegalStateException(
                "simulated NESTED inner failure"
        );
    }
}
