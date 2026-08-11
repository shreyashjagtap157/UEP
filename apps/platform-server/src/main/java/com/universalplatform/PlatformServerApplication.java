package com.universalplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

@Modulith(systemName = "Universal Education Platform")
public class PlatformServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformServerApplication.class, args);
    }
}
