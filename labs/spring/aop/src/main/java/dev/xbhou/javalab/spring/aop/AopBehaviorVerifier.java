package dev.xbhou.javalab.spring.aop;

import java.util.List;
import org.springframework.aop.support.AopUtils;

public final class AopBehaviorVerifier {

    private AopBehaviorVerifier() {
    }

    public static void verifyProxyTypes(
            PaymentService paymentService,
            ClassOnlyService classOnlyService
    ) {
        if (!AopUtils.isJdkDynamicProxy(paymentService)) {
            throw new IllegalStateException(
                    "PaymentService should use JDK dynamic proxy"
            );
        }

        if (!AopUtils.isCglibProxy(classOnlyService)) {
            throw new IllegalStateException(
                    "ClassOnlyService should use class-based proxy"
            );
        }
    }

    public static void verifySelfInvocation(
            List<String> actual
    ) {
        verify(
                "self invocation",
                List.of(
                        "advice before outer",
                        "target outer start",
                        "target inner",
                        "target outer end",
                        "advice after outer"
                ),
                actual
        );
    }

    public static void verifyDirectInnerCall(
            List<String> actual
    ) {
        verify(
                "direct inner call",
                List.of(
                        "advice before inner",
                        "target inner",
                        "advice after inner"
                ),
                actual
        );
    }

    public static void verifyClassProxy(
            List<String> actual
    ) {
        verify(
                "class proxy",
                List.of(
                        "advice before run",
                        "target classOnly run",
                        "advice after run"
                ),
                actual
        );
    }

    private static void verify(
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
                scenario + " verified successfully."
        );
    }
}
