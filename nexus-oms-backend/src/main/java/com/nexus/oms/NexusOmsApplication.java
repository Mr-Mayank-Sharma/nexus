package com.nexus.oms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NexusOmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusOmsApplication.class, args);
    }
}
