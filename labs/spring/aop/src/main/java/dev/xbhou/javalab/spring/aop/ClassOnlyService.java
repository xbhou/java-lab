package dev.xbhou.javalab.spring.aop;

public class ClassOnlyService {

    private final AopEventRecorder recorder;

    public ClassOnlyService(AopEventRecorder recorder) {
        this.recorder = recorder;
    }

    @Traceable
    public void run() {
        recorder.record("target classOnly run");
    }
}
