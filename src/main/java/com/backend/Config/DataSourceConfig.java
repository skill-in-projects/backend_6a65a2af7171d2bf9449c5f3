package com.backend.Config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DataSourceConfig {

    private String safeDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // If decoding fails (e.g., invalid % encoding), return original value
            return value;
        }
    }

    @Bean
    public DataSource dataSource() {
        String databaseUrl = System.getenv("DATABASE_URL");
        
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            throw new IllegalStateException("DATABASE_URL environment variable is not set");
        }

        try {
            // Parse PostgreSQL URL format: postgresql://user:password@host:port/database
            URI dbUri = new URI(databaseUrl);
            
            String userInfo = dbUri.getUserInfo();
            String username;
            String password;
            
            if (userInfo != null && userInfo.contains(":")) {
                int colonIndex = userInfo.indexOf(":");
                username = userInfo.substring(0, colonIndex);
                password = userInfo.substring(colonIndex + 1);
            } else {
                username = userInfo != null ? userInfo : "";
                password = "";
            }
            
            // Safely decode username and password
            username = safeDecode(username);
            password = safeDecode(password);
            
            String host = dbUri.getHost();
            int port = dbUri.getPort() > 0 ? dbUri.getPort() : 5432;
            String database = dbUri.getPath().replaceFirst("/", "");
            
            // Build JDBC URL
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            
            // Add query parameters if present (e.g., sslmode)
            if (dbUri.getQuery() != null && !dbUri.getQuery().isEmpty()) {
                jdbcUrl += "?" + dbUri.getQuery();
            }
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            // BUILD-VERIFY-MARKER-20260721-01: if you can see this comment in a newly generated
            // board's DataSourceConfig.java, the running strAppersBackend process is on this branch.
            // Let the pool drain to zero connections when idle so Neon's compute can auto-suspend
            // instead of being held active indefinitely by parked connections.
            config.setMinimumIdle(0);
            config.setIdleTimeout(60000);
            
            return new HikariDataSource(config);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse DATABASE_URL: " + e.getMessage(), e);
        }
    }
}
