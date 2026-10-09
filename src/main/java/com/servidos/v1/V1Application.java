package com.servidos.v1;

import com.servidos.v1.identity.infrastructure.security.AuthProperties;
import com.servidos.v1.shared.security.RateLimitingFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class V1Application {

	/**
	 * Hora del negocio. Las fechas se guardan como LocalDate/LocalDateTime sin zona
	 * (created_at, vigencia de suscripciones), así que "hoy" y "ahora" tienen que ser los
	 * de Perú y no los del servidor donde corra. Los tests usan la misma zona (pom, surefire).
	 */
	public static final String ZONA_HORARIA = "America/Lima";

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(ZONA_HORARIA));
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
