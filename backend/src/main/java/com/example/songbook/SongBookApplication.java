package com.example.songbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SongBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(SongBookApplication.class, args);
    }
}
