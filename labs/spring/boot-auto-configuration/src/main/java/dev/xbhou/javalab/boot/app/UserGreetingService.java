package dev.xbhou.javalab.boot.app;

import dev.xbhou.javalab.boot.autoconfigure.GreetingService;

public class UserGreetingService
        implements GreetingService {

    @Override
    public String greet(String name) {
        return "Custom, " + name;
    }
}
