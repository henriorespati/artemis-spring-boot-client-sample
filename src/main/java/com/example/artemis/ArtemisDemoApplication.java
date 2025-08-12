package com.example.artemis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ArtemisDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArtemisDemoApplication.class, args);
    }
}
