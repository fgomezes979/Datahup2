package com.linkedin.metadata.kafka.config;

import org.neo4j.driver.v1.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.linkedin.common.factory.Neo4jDriverFactory;
import com.linkedin.metadata.dao.internal.BaseGraphWriterDAO;
import com.linkedin.metadata.dao.internal.Neo4jGraphWriterDAO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@Import({Neo4jDriverFactory.class})
@RequiredArgsConstructor
public class Neo4jConfig {

    private final Driver neo4jDriver;

    @Bean
     public BaseGraphWriterDAO graphWriterDAO() {
        BaseGraphWriterDAO graphWriterDAO = null;
        try {
            graphWriterDAO = new Neo4jGraphWriterDAO(neo4jDriver);
        } catch (Exception e) {
            log.error("Error in initializing Neo4j.", e);
        }
        log.info("Neo4jDriver built successfully");

        return graphWriterDAO;
    }
}
