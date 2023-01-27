package com.linkedin.gms.factory.config;

import com.datahub.authentication.AuthenticationConfiguration;
import com.datahub.authorization.AuthorizationConfiguration;
import com.linkedin.datahub.graphql.featureflags.FeatureFlags;
import com.linkedin.metadata.config.DataHubConfiguration;
import com.linkedin.metadata.config.ElasticSearchConfiguration;
import com.linkedin.metadata.config.IngestionConfiguration;
import com.linkedin.metadata.config.SystemUpdateConfiguration;
import com.linkedin.metadata.config.TestsConfiguration;
import com.linkedin.metadata.config.ViewsConfiguration;
import com.linkedin.metadata.config.VisualConfiguration;
import com.linkedin.metadata.telemetry.TelemetryConfiguration;
import com.linkedin.gms.factory.spring.YamlPropertySourceFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;


@Configuration
@ConfigurationProperties
@PropertySource(value = "classpath:/application.yml", factory = YamlPropertySourceFactory.class)
@Data
public class ConfigurationProvider {
  /**
   * Authentication related configs
   */
  private AuthenticationConfiguration authentication;
  /**
   * Authorizer related configs
   */
  private AuthorizationConfiguration authorization;
  /**
   * Ingestion related configs
   */
  private IngestionConfiguration ingestion;
  /**
   * Telemetry related configs
   */
  private TelemetryConfiguration telemetry;
  /**
   * Viz related configs
   */
  private VisualConfiguration visualConfig;
  /**
   * Tests related configs
   */
  private TestsConfiguration metadataTests;
  /**
   * DataHub top-level server configurations
   */
  private DataHubConfiguration datahub;
  /**
   * Views feature related configs
   */
  private ViewsConfiguration views;
  /**
   * Feature flags indicating what is turned on vs turned off
   */
  private FeatureFlags featureFlags;

  /**
   * ElasticSearch configurations
   */
  private ElasticSearchConfiguration elasticSearch;

  /**
   * System Update configurations
   */
  private SystemUpdateConfiguration systemUpdate;
}
