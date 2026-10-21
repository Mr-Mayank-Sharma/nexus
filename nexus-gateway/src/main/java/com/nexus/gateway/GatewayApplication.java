package com.nexus.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Nexus Gateway: one API every Nexus installation calls for carrier rates, labels, voids, return labels, address
 * checks and tracking, and for customer emails. Carrier and email credentials live only here, per client and store.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(GatewayApplication.class, args);
    }
}
