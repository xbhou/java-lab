package dev.xbhou.javalab.spring.aop;

public class PaymentServiceImpl implements PaymentService {

    private final AopEventRecorder recorder;

    public PaymentServiceImpl(AopEventRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    @Traceable
    public void outer() {
        recorder.record("target outer start");

        this.inner();

        recorder.record("target outer end");
    }

    @Override
    @Traceable
    public void inner() {
        recorder.record("target inner");
    }
}
