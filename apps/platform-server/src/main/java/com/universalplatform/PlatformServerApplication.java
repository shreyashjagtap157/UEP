package com.universalplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableScheduling;

@Modulith(systemName = "Universal Education Platform")
@EnableScheduling
public class PlatformServerApplication {
    public static void main(String[] args) {

        SpringApplication.run(PlatformServerApplication.class, args);
    }
}
