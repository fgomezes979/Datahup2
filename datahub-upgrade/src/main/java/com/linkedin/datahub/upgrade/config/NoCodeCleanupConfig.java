package com.linkedin.datahub.upgrade.config;

import com.linkedin.datahub.upgrade.nocodecleanup.NoCodeCleanupUpgrade;
import com.linkedin.metadata.graph.Neo4jGraphClient;
import io.ebean.EbeanServerFactory;
import io.ebean.config.ServerConfig;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import static com.linkedin.metadata.entity.ebean.EbeanAspectDao.*;


@Configuration
public class NoCodeCleanupConfig {

  @Autowired
  ApplicationContext applicationContext;

  @Bean(name = "noCodeCleanup")
  @DependsOn({"gmsEbeanServiceConfig", "neo4jGraphClient"})
  @Nonnull
  public NoCodeCleanupUpgrade createInstance() {
    final ServerConfig serverConfig = applicationContext.getBean(ServerConfig.class);
    final Neo4jGraphClient graphClient = applicationContext.getBean(Neo4jGraphClient.class);
    if (!serverConfig.getPackages().contains(EBEAN_MODEL_PACKAGE)) {
      serverConfig.getPackages().add(EBEAN_MODEL_PACKAGE);
    }
    return new NoCodeCleanupUpgrade(EbeanServerFactory.create(serverConfig), graphClient);
  }
}
