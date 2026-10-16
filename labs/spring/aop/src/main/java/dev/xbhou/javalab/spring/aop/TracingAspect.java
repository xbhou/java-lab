package dev.xbhou.javalab.spring.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TracingAspect {

    private final AopEventRecorder recorder;

    public TracingAspect(AopEventRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("@annotation(dev.xbhou.javalab.spring.aop.Traceable)")
    public Object trace(ProceedingJoinPoint joinPoint)
            throws Throwable {
        String method =
                joinPoint.getSignature().getName();

        recorder.record("advice before " + method);

        try {
            return joinPoint.proceed();
        } finally {
            recorder.record("advice after " + method);
        }
    }
}
