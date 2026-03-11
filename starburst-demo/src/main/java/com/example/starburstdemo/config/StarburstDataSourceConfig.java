package com.example.starburstdemo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class StarburstDataSourceConfig {

    @Value("${starburst.jdbc.url}")
    private String jdbcUrl;

    @Value("${starburst.jdbc.username}")
    private String username;

    @Value("${starburst.jdbc.password:}")
    private String password;

    @Bean
    public DataSource starburstDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("io.trino.jdbc.TrinoDriver");

        // Trino does not support JDBC isValid(); a lightweight query is required
        config.setConnectionTestQuery("SELECT 1");
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(10);
        config.setIdleTimeout(30_000);
        config.setConnectionTimeout(30_000);

        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate starburstJdbcTemplate(DataSource starburstDataSource) {
        return new JdbcTemplate(starburstDataSource);
    }
}
