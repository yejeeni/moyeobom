package com.moyeobom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MoyeobomApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoyeobomApplication.class, args);
	}

}
