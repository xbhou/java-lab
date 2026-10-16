package dev.xbhou.javalab.boot.app;

import dev.xbhou.javalab.boot.autoconfigure.GreetingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UserGreetingConfiguration {

    @Bean
    public GreetingService userGreetingService() {
        return new UserGreetingService();
    }
}
