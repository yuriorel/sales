package com.example.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Entry point of the **Sales Online** e-commerce engine.
///
/// Runs on virtual threads (`spring.threads.virtual.enabled`) and serves the
/// versioned REST API over HTTPS.
@SpringBootApplication
public class SalesApplication {

    static void main(String[] args) {
        SpringApplication.run(SalesApplication.class, args);
    }
}
