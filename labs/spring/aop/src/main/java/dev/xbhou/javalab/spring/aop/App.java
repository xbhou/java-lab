package dev.xbhou.javalab.spring.aop;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             AopConfig.class
                     )) {

            AopEventRecorder recorder =
                    context.getBean(AopEventRecorder.class);

            PaymentService paymentService =
                    context.getBean(PaymentService.class);

            ClassOnlyService classOnlyService =
                    context.getBean(ClassOnlyService.class);

            printProxyTypes(
                    paymentService,
                    classOnlyService
            );

            AopBehaviorVerifier.verifyProxyTypes(
                    paymentService,
                    classOnlyService
            );

            System.out.println();
            System.out.println(
                    "=== Scenario 1: self invocation ==="
            );

            recorder.reset();
            paymentService.outer();

            AopBehaviorVerifier.verifySelfInvocation(
                    recorder.snapshot()
            );

            System.out.println();
            System.out.println(
                    "=== Scenario 2: direct inner call ==="
            );

            recorder.reset();
            paymentService.inner();

            AopBehaviorVerifier.verifyDirectInnerCall(
                    recorder.snapshot()
            );

            System.out.println();
            System.out.println(
                    "=== Scenario 3: class-based proxy ==="
            );

            recorder.reset();
            classOnlyService.run();

            AopBehaviorVerifier.verifyClassProxy(
                    recorder.snapshot()
            );
        }
    }

    private static void printProxyTypes(
            PaymentService paymentService,
            ClassOnlyService classOnlyService
    ) {
        System.out.println("=== Proxy types ===");

        System.out.println(
                "paymentService class="
                        + paymentService.getClass().getName()
        );

        System.out.println(
                "paymentService jdkProxy="
                        + AopUtils.isJdkDynamicProxy(
                        paymentService
                )
        );

        System.out.println(
                "classOnlyService class="
                        + classOnlyService.getClass().getName()
        );

        System.out.println(
                "classOnlyService classProxy="
                        + AopUtils.isCglibProxy(
                        classOnlyService
                )
        );
    }
}
