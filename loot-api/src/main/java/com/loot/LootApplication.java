package com.loot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class LootApplication {

	public static void main(String[] args) {

		SpringApplication.run(LootApplication.class, args);
		
	}

}
