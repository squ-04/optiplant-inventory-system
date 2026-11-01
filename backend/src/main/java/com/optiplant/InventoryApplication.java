package com.optiplant;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Entry point of the inventory monolith. The default in-memory user store is excluded because accounts live in the
 * identity module, and time is exposed as a UTC {@link Clock} bean so that time-based rules can be tested deterministically.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class InventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
