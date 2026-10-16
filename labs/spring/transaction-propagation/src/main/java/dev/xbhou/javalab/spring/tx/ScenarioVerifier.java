package dev.xbhou.javalab.spring.tx;

import java.util.List;

public final class ScenarioVerifier {

    private ScenarioVerifier() {
    }

    public static void verify(
            String scenario,
            List<String> expected,
            List<String> actual
    ) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "%s mismatch%nexpected=%s%nactual=%s"
                            .formatted(
                                    scenario,
                                    expected,
                                    actual
                            )
            );
        }

        System.out.println(
                scenario + " verified: " + actual
        );
    }
}
