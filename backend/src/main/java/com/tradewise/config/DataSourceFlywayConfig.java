package com.tradewise.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Explicit DataSource + Flyway wiring.
 *
 * <p>Flyway migrations run inside the DataSource bean's creation, which guarantees the
 * schema is migrated before Hibernate starts and validates it (ddl-auto: validate) —
 * no reliance on auto-configuration ordering. Flyway owns the schema; JPA never mutates it.
 */
@Configuration
public class DataSourceFlywayConfig {

    @ConfigurationProperties(prefix = "spring.datasource")
    public record DataSourceSettings(String url, String username, String password) {
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSourceSettings settings) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(settings.url());
        dataSource.setUsername(settings.username());
        dataSource.setPassword(settings.password());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        return dataSource;
    }
}
