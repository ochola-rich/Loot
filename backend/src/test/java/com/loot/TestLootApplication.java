package com.loot;

import org.springframework.boot.SpringApplication;

public class TestLootApplication {

	public static void main(String[] args) {
		SpringApplication.from(LootApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
