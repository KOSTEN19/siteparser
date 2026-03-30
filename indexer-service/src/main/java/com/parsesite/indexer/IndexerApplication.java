package com.parsesite.indexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.parsesite")
public class IndexerApplication {
    public static void main(String[] args) {
        SpringApplication.run(IndexerApplication.class, args);
    }
}
