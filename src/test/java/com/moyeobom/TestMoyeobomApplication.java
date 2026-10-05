package com.moyeobom;

import org.springframework.boot.SpringApplication;

public class TestMoyeobomApplication {

	public static void main(String[] args) {
		SpringApplication.from(MoyeobomApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
