package com.japaneselearning.learning;

import com.japaneselearning.learning.config.ServiceEndpointProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ServiceEndpointProperties.class)
public class LearningApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningApiApplication.class, args);
    }

}
