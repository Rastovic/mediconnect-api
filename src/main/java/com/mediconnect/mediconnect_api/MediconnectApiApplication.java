package com.mediconnect.mediconnect_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.mediconnect")
@EntityScan("com.mediconnect.entity")
@EnableJpaRepositories("com.mediconnect.repository")
public class MediconnectApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediconnectApiApplication.class, args);
    }
}
