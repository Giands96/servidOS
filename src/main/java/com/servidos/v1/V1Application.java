package com.servidos.v1;

import com.servidos.v1.identity.infrastructure.security.AuthProperties;
import com.servidos.v1.shared.security.RateLimitingFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class V1Application {

	public static void main(String[] args) {
		SpringApplication.run(V1Application.class, args);
	}


	@Bean
	public FilterRegistrationBean<RateLimitingFilter> filterRateLimiting() {
		FilterRegistrationBean<RateLimitingFilter> register = new FilterRegistrationBean<>();
		register.setFilter(new RateLimitingFilter());
		register.addUrlPatterns("/api/v1/auth/login", "/api/v1/auth/refresh");
		return register;
	}



}
