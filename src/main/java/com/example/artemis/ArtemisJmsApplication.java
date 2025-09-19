package com.example.artemis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration;
import org.springframework.jms.annotation.EnableJms;

// @SpringBootApplication(exclude = {JmsAutoConfiguration.class})
@SpringBootApplication
@EnableJms
public class ArtemisJmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArtemisJmsApplication.class, args);
    }
}
