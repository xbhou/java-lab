package dev.xbhou.javalab.boot.app;

import dev.xbhou.javalab.boot.autoconfigure.DefaultGreetingService;
import dev.xbhou.javalab.boot.autoconfigure.GreetingService;
import java.util.Map;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class App {

    public static void main(String[] args) {
        defaultAutoConfiguration();
        propertyDisablesAutoConfiguration();
        userBeanMakesAutoConfigurationBackOff();

        System.out.println();
        System.out.println(
                "All auto-configuration scenarios verified."
        );
    }

    private static void defaultAutoConfiguration() {
        System.out.println();
        System.out.println(
                "=== Scenario 1: default auto-configuration ==="
        );

        try (ConfigurableApplicationContext context =
                     builder(DemoApplication.class).run()) {

            Map<String, GreetingService> beans =
                    context.getBeansOfType(
                            GreetingService.class
                    );

            verifyCount("default", beans, 1);

            GreetingService service =
                    beans.values().iterator().next();

            if (!(service
                    instanceof DefaultGreetingService)) {
                throw new IllegalStateException(
                        "default scenario should use "
                                + "DefaultGreetingService"
                );
            }

            verifyGreeting(
                    "default",
                    "Hello, Vincent",
                    service.greet("Vincent")
            );

            printBeans(beans);
            ConditionReportPrinter.print(context);
        }
    }

    private static void propertyDisablesAutoConfiguration() {
        System.out.println();
        System.out.println(
                "=== Scenario 2: property disables auto-configuration ==="
        );

        try (ConfigurableApplicationContext context =
                     builder(DemoApplication.class)
                             .properties(
                                     "demo.greeting.enabled=false"
                             )
                             .run()) {

            Map<String, GreetingService> beans =
                    context.getBeansOfType(
                            GreetingService.class
                    );

            verifyCount("disabled", beans, 0);

            printBeans(beans);
            ConditionReportPrinter.print(context);
        }
    }

    private static void userBeanMakesAutoConfigurationBackOff() {
        System.out.println();
        System.out.println(
                "=== Scenario 3: user bean makes auto-configuration back off ==="
        );

        try (ConfigurableApplicationContext context =
                     builder(
                             DemoApplication.class,
                             UserGreetingConfiguration.class
                     ).run()) {

            Map<String, GreetingService> beans =
                    context.getBeansOfType(
                            GreetingService.class
                    );

            verifyCount("user override", beans, 1);

            GreetingService service =
                    beans.values().iterator().next();

            if (!(service instanceof UserGreetingService)) {
                throw new IllegalStateException(
                        "user override scenario should use "
                                + "UserGreetingService"
                );
            }

            verifyGreeting(
                    "user override",
                    "Custom, Vincent",
                    service.greet("Vincent")
            );

            printBeans(beans);
            ConditionReportPrinter.print(context);
        }
    }

    private static SpringApplicationBuilder builder(
            Class<?>... sources
    ) {
        return new SpringApplicationBuilder(sources)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties(
                        "logging.level.root=OFF"
                );
    }

    private static void printBeans(
            Map<String, GreetingService> beans
    ) {
        System.out.println(
                "GreetingService count=" + beans.size()
        );

        beans.forEach(
                (name, bean) -> {
                    System.out.println(
                            "bean name=" + name
                    );
                    System.out.println(
                            "bean class="
                                    + bean.getClass()
                                    .getSimpleName()
                    );
                    System.out.println(
                            "greeting="
                                    + bean.greet("Vincent")
                    );
                }
        );
    }

    private static void verifyCount(
            String scenario,
            Map<String, GreetingService> beans,
            int expected
    ) {
        if (beans.size() != expected) {
            throw new IllegalStateException(
                    scenario
                            + " expected bean count "
                            + expected
                            + " but got "
                            + beans.size()
            );
        }
    }

    private static void verifyGreeting(
            String scenario,
            String expected,
            String actual
    ) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    scenario
                            + " expected greeting "
                            + expected
                            + " but got "
                            + actual
            );
        }
    }
}
