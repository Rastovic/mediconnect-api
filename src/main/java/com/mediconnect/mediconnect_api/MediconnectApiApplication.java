package com.mediconnect.mediconnect_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// [A10] @EnableScheduling activates the @Scheduled worker in RefillQueueService.runWorker,
//        which polls the refill queue every 30 s. The worker swallows every exception
//        in a single catch (Exception), so any failure during a tick is silently lost.
@SpringBootApplication(scanBasePackages = "com.mediconnect")
@EntityScan({"com.mediconnect.entity", "com.mediconnect.ctf.entity"})
@EnableJpaRepositories({"com.mediconnect.repository", "com.mediconnect.ctf.repository"})
@EnableScheduling
public class MediconnectApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediconnectApiApplication.class, args);
    }
}
