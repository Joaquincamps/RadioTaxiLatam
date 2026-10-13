package com.example.RadioTaxiLatam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RadioTaxiLatamApplication {

	public static void main(String[] args) {
		SpringApplication.run(RadioTaxiLatamApplication.class, args);
	}

}
