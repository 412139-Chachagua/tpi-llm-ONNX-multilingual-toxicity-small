package com.tpi.toxicity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ToxicityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToxicityServiceApplication.class, args);
    }
}
