package com.servidos.v1.shared.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileConfigurationTests {

    @TempDir
    Path directory;

    @Test
    void productionRejectsAllMissingSecretsBeforeCreatingBeans() {
        assertThatThrownBy(() -> start("prod", Map.of()))
                .hasMessageContaining("DB_USERNAME", "DB_PASSWORD", "JWT_SECRET");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_USERNAME", "DB_PASSWORD", "JWT_SECRET"})
    void productionRejectsEachMissingSecret(String name) {
        var values = secrets();
        values.remove(name);
        assertThatThrownBy(() -> start("prod", values))
                .hasMessageContaining(name)
                .hasMessageNotContaining("synthetic-password")
                .hasMessageNotContaining("synthetic-jwt");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_USERNAME", "DB_PASSWORD", "JWT_SECRET"})
    void productionRejectsBlankSecrets(String name) {
        var values = secrets();
        values.put(name, "   ");
        assertThatThrownBy(() -> start("prod", values)).hasMessageContaining(name);
    }

    @Test
    void productionUsesExternalCredentialsAndQuietLogging() {
        try (var context = start("prod", secrets())) {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("synthetic-user");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo("synthetic-password");
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(env.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
            assertThat(env.getProperty("debug", Boolean.class)).isFalse();
            assertThat(env.getProperty("logging.level.org.flywaydb", "INFO")).isEqualTo("INFO");
        }
    }

    @Test
    void productionDoesNotImportDevelopmentEnvFile() throws Exception {
        writeEnv();
        assertThatThrownBy(() -> start("prod", Map.of()))
                .hasMessageContaining("DB_USERNAME", "DB_PASSWORD", "JWT_SECRET");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod,dev", "dev,prod", "prod,test", "test,prod"})
    void productionCannotBeCombinedWithNonProductionProfiles(String profiles) {
        assertThatThrownBy(() -> start(profiles, secrets()))
                .hasMessageContaining("prod", "dev", "test");
    }

    @Test
    void developmentLoadsEnvFileWithoutShellExport() throws Exception {
        writeEnv();
        try (var context = start("dev", Map.of())) {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("dotenv-user");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo("dotenv-password");
            assertThat(env.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://127.0.0.1:65432/dotenv_test");
        }
    }

    @Test
    void externalValuesOverrideDevelopmentEnvFile() throws Exception {
        writeEnv();
        try (var context = start("dev", secrets())) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                    .isEqualTo("synthetic-user");
        }
    }

    @Test
    void testProfileUsesOnlyExplicitTestDatabase() throws Exception {
        writeEnv();
        try (var context = start("test", Map.of(
                "TEST_DB_URL", "jdbc:postgresql://127.0.0.1:65433/isolated_test",
                "TEST_DB_USERNAME", "isolated-user", "TEST_DB_PASSWORD", "isolated-password"))) {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://127.0.0.1:65433/isolated_test");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("isolated-user");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo("isolated-password");
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(env.getProperty("DB_USERNAME")).isNull();
        }
    }

    private ConfigurableApplicationContext start(String profiles, Map<String, Object> values) {
        var environment = new StandardEnvironment();
        // Do not inherit the developer's credentials or external configuration in these tests.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        var properties = new HashMap<>(values);
        properties.put("user.dir", directory.toString().replace('\\', '/'));
        properties.put("spring.config.location", "classpath:/application.yaml");
        properties.put("spring.profiles.active", profiles);
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-test", properties));
        var application = new SpringApplication(EmptyConfiguration.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        return application.run();
    }

    private void writeEnv() throws Exception {
        Files.writeString(directory.resolve(".env"), """
                DB_URL=jdbc:postgresql://127.0.0.1:65432/dotenv_test
                DB_USERNAME=dotenv-user
                DB_PASSWORD=dotenv-password
                JWT_SECRET=dotenv-jwt
                """);
    }

    private Map<String, Object> secrets() {
        return new HashMap<>(Map.of("DB_USERNAME", "synthetic-user",
                "DB_PASSWORD", "synthetic-password", "JWT_SECRET", "synthetic-jwt"));
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }
}
