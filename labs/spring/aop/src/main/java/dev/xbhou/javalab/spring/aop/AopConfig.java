package dev.xbhou.javalab.spring.aop;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AopConfig {

    @Bean
    public AopEventRecorder aopEventRecorder() {
        return new AopEventRecorder();
    }

    @Bean
    public TracingAspect tracingAspect(
            AopEventRecorder recorder
    ) {
        return new TracingAspect(recorder);
    }

    @Bean
    public PaymentService paymentService(
            AopEventRecorder recorder
    ) {
        return new PaymentServiceImpl(recorder);
    }

    @Bean
    public ClassOnlyService classOnlyService(
            AopEventRecorder recorder
    ) {
        return new ClassOnlyService(recorder);
    }
}
