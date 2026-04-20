package com.gavel.bid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.gavel.bid", "com.gavel.shared"})
public class BidApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidApplication.class, args);
    }
}
