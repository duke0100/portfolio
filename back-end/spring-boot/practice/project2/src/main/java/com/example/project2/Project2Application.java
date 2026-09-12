package com.example.project2;

import com.example.commonlib.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// Interview topic: docs/interview/spring-boot/02-test-slices-vs-mockito.md#webmvctest
// common-lib's shared beans live in a different package, so we tell Spring to scan it too -
// otherwise they'd never be picked up, especially under @WebMvcTest.
@SpringBootApplication(scanBasePackages = {"com.example.project2", "com.example.commonlib"})
@Import(SwaggerConfig.class)
public class Project2Application {

	public static void main(String[] args) {
		SpringApplication.run(Project2Application.class, args);
	}

}
