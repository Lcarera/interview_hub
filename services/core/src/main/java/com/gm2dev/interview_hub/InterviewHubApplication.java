package com.gm2dev.interview_hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
public class InterviewHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(InterviewHubApplication.class, args);
	}

}
