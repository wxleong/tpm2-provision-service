package com.infineon.tpm20;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class Tpm20Application {

	public static void main(String[] args) {
		SpringApplication.run(Tpm20Application.class, args);
	}

}
