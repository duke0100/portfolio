package com.example.project3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// common-lib's beans (GlobalExceptionHandler, SwaggerConfig) live in the sibling
// com.example.commonlib package, outside the default component-scan reach of this
// @SpringBootApplication class - scanBasePackages pulls them in explicitly.
@SpringBootApplication(scanBasePackages = {"com.example.project3", "com.example.commonlib"})
public class Project3Application {

	public static void main(String[] args) {
		SpringApplication.run(Project3Application.class, args);
	}

}
