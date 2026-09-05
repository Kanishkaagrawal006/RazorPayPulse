package com.razorpay.RazorPayPulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.razorpay")
@EnableJpaRepositories(basePackages = "com.razorpay.repository")
@EntityScan(basePackages = "com.razorpay.entity")
public class RazorPayPulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(RazorPayPulseApplication.class, args);
	}

}
