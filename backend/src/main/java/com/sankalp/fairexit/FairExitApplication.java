package com.sankalp.fairexit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Users live in our own table and log in with JWT, so Boot's default in-memory user is not wanted
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class FairExitApplication {

    public static void main(String[] args) {
        SpringApplication.run(FairExitApplication.class, args);
    }
}
