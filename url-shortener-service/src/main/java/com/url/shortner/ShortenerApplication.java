package com.url.shortner;

import com.url.shortner.dto.SecurityProperties;
import com.url.shortner.dto.ShortenerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ShortenerProperties.class, SecurityProperties.class})
public class ShortenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShortenerApplication.class, args);
    }
}
