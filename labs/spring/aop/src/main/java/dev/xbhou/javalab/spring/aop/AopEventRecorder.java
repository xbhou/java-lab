package dev.xbhou.javalab.spring.aop;

import java.util.ArrayList;
import java.util.List;

public class AopEventRecorder {

    private final List<String> events = new ArrayList<>();

    public synchronized void record(String event) {
        events.add(event);
        System.out.println(event);
    }

    public synchronized List<String> snapshot() {
        return List.copyOf(events);
    }

    public synchronized void reset() {
        events.clear();
    }
}
