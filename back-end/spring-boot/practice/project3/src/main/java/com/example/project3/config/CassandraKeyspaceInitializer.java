package com.example.project3.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.net.InetSocketAddress;
import java.sql.Driver;

@Configuration
public class CassandraKeyspaceInitializer {

    @Bean
    public InitializingBean cassandraKeyspaceCreator(
            @Value("${spring.cassandra.contact-points}") String contactPoints,
            @Value("${spring.cassandra.port:9042}") int port,
            @Value("${spring.cassandra.local-datacenter}") String localDc,
            @Value("${spring.cassandra.keyspace-name}") String keyspace) {
        return () -> {
            try (CqlSession session = CqlSession.builder()
                    .addContactPoint(new InetSocketAddress(contactPoints, port))
                    .withLocalDatacenter(localDc)
                    .build()) {
                session.execute(String.format(
                        "CREATE KEYSPACE IF NOT EXISTS %s " +
                        "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1} " +
                        "AND durable_writes = true",
                        keyspace));
            }
        };
    }

    /**
     * Overrides the auto-configured CqlSession bean (which is @ConditionalOnMissingBean)
     * so we can add @DependsOn("cassandraKeyspaceCreator"). The auto-configured
     * CqlSessionBuilder already has all spring.cassandra.* settings applied to it;
     * we just delay building the actual session until the keyspace exists.
     */
    @Bean
    @DependsOn("cassandraKeyspaceCreator")
    public CqlSession cassandraSession(CqlSessionBuilder cqlSessionBuilder) {
        return cqlSessionBuilder.build();
    }

    /**
     * Overrides Spring Boot's auto-configured SpringLiquibase bean
     * (@ConditionalOnMissingBean) to guarantee it runs after the keyspace is created.
     */
    @Bean
    @DependsOn("cassandraKeyspaceCreator")
    public SpringLiquibase liquibase(
            @Value("${spring.liquibase.url}") String url,
            @Value("${spring.liquibase.user}") String username,
            @Value("${spring.liquibase.password}") String password,
            @Value("${spring.liquibase.change-log}") String changeLog,
            @Value("${spring.liquibase.driver-class-name}") String driverClassName) throws Exception {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriver((Driver) Class.forName(driverClassName).getDeclaredConstructor().newInstance());

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        return liquibase;
    }
}
