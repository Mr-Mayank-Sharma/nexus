package com.nexus.oms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class NexusOmsApplication {

    /**
     * Every timestamp in Nexus is a zone-less LocalDateTime meaning UTC. Pinning the JVM
     * zone makes LocalDateTime.now() UTC on every host (it followed the server's local zone,
     * so an IST laptop and a UTC container wrote different wall-clock values).
     */
    static void pinUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        pinUtc();
        SpringApplication.run(NexusOmsApplication.class, args);
    }
}
