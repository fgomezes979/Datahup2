package com.linkedin.gms.factory.common;

import com.linkedin.gms.factory.spring.YamlPropertySourceFactory;
import com.linkedin.metadata.utils.metrics.MetricUtils;
import io.ebean.config.DatabaseConfig;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourcePoolListener;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;


@Slf4j
@Configuration
@PropertySource(value = "classpath:/application.yml", factory = YamlPropertySourceFactory.class)
public class EbeanDatabaseConfigFactory {

  @Value("${ebean.username}")
  private String ebeanDatasourceUsername;

  @Value("${ebean.password}")
  private String ebeanDatasourcePassword;

  @Value("${ebean.driver}")
  private String ebeanDatasourceDriver;

  @Value("${ebean.minConnections:2}")
  private Integer ebeanMinConnections;

  @Value("${ebean.maxConnections:50}")
  private Integer ebeanMaxConnections;

  @Value("${ebean.maxInactiveTimeSeconds:120}")
  private Integer ebeanMaxInactiveTimeSecs;

  @Value("${ebean.maxAgeMinutes:120}")
  private Integer ebeanMaxAgeMinutes;

  @Value("${ebean.leakTimeMinutes:15}")
  private Integer ebeanLeakTimeMinutes;

  @Value("${ebean.waitTimeoutMillis:1000}")
  private Integer ebeanWaitTimeoutMillis;

  @Value("${ebean.autoCreateDdl:false}")
  private Boolean ebeanAutoCreate;

  @Value("${ebean.postgresUseIamAuth:false}")
  private Boolean postgresUseIamAuth;

  @Value("${ebean.primary.minConnections:2}")
  private Integer ebeanPrimaryMinConnections;

  @Value("${ebean.primary.maxConnections:25}")
  private Integer ebeanPrimaryMaxConnections;

  @Value("${ebean.primary.maxInactiveTimeSeconds:120}")
  private Integer ebeanPrimaryMaxInactiveTimeSecs;

  @Value("${ebean.primary.maxAgeMinutes:120}")
  private Integer ebeanPrimaryMaxAgeMinutes;

  @Value("${ebean.primary.leakTimeMinutes:15}")
  private Integer ebeanPrimaryLeakTimeMinutes;

  @Value("${ebean.primary.waitTimeoutMillis:1000}")
  private Integer ebeanPrimaryWaitTimeoutMillis;

  public static DataSourcePoolListener getListenerToTrackCounts(String metricName) {
    final String counterName = "ebeans_connection_pool_size_" + metricName;
    return new DataSourcePoolListener() {
      @Override
      public void onAfterBorrowConnection(Connection connection) {
        MetricUtils.counter(counterName).inc();
      }

      @Override
      public void onBeforeReturnConnection(Connection connection) {
        MetricUtils.counter(counterName).dec();
      }
    };
  }

  @Value("${ebean.url}")
  protected String dataSourceUrl;

  @Bean("gmsEbeanServiceConfig")
  protected DatabaseConfig gmsEbeanServiceConfig() {
    DataSourceConfig dataSourceConfig = new DataSourceConfig();
    dataSourceConfig.setUsername(ebeanDatasourceUsername);
    dataSourceConfig.setPassword(ebeanDatasourcePassword);
    dataSourceConfig.setUrl(dataSourceUrl);
    dataSourceConfig.setDriver(ebeanDatasourceDriver);
    dataSourceConfig.setMinConnections(ebeanMinConnections);
    dataSourceConfig.setMaxConnections(ebeanMaxConnections);
    dataSourceConfig.setMaxInactiveTimeSecs(ebeanMaxInactiveTimeSecs);
    dataSourceConfig.setMaxAgeMinutes(ebeanMaxAgeMinutes);
    dataSourceConfig.setLeakTimeMinutes(ebeanLeakTimeMinutes);
    dataSourceConfig.setWaitTimeoutMillis(ebeanWaitTimeoutMillis);
    dataSourceConfig.setListener(getListenerToTrackCounts("read"));
    // Adding IAM auth access for AWS Postgres
    if (postgresUseIamAuth) {
      Map<String, String> custom = new HashMap<>();
      custom.put("wrapperPlugins", "iam");
      dataSourceConfig.setCustomProperties(custom);
    }

    DatabaseConfig serverConfig = new DatabaseConfig();
    serverConfig.setName("gmsEbeanServiceConfig");
    serverConfig.setDataSourceConfig(dataSourceConfig);
    serverConfig.setDdlGenerate(ebeanAutoCreate);
    serverConfig.setDdlRun(ebeanAutoCreate);
    return serverConfig;
  }

  @Value("${ebean.primary.url}")
  protected String primaryDataSourceUrl;

  @Bean("gmsEbeanPrimaryServiceConfig")
  protected DatabaseConfig gmsEbeanPrimaryServiceConfig() {
    final String writeDataSourceUrl;

    if (primaryDataSourceUrl == null) {
      log.info("EBEAN_PRIMARY_DATASOURCE_URL is not defined, defaulting to base ebean configuration for writes.");
      writeDataSourceUrl = dataSourceUrl;
    } else {
      log.info("Write ebean data source is active.");
      writeDataSourceUrl = primaryDataSourceUrl;
    }

    DataSourceConfig dataSourceConfig = new DataSourceConfig();
    dataSourceConfig.setUsername(ebeanDatasourceUsername);
    dataSourceConfig.setPassword(ebeanDatasourcePassword);
    dataSourceConfig.setUrl(writeDataSourceUrl);
    dataSourceConfig.setDriver(ebeanDatasourceDriver);
    dataSourceConfig.setMinConnections(ebeanPrimaryMinConnections);
    dataSourceConfig.setMaxConnections(ebeanPrimaryMaxConnections);
    dataSourceConfig.setMaxInactiveTimeSecs(ebeanPrimaryMaxInactiveTimeSecs);
    dataSourceConfig.setMaxAgeMinutes(ebeanPrimaryMaxAgeMinutes);
    dataSourceConfig.setLeakTimeMinutes(ebeanPrimaryLeakTimeMinutes);
    dataSourceConfig.setWaitTimeoutMillis(ebeanPrimaryWaitTimeoutMillis);
    dataSourceConfig.setListener(getListenerToTrackCounts("primary"));
    // Adding IAM auth access for AWS Postgres
    if (postgresUseIamAuth) {
      Map<String, String> custom = new HashMap<>();
      custom.put("wrapperPlugins", "iam");
      dataSourceConfig.setCustomProperties(custom);
    }

    DatabaseConfig serverConfig = new DatabaseConfig();
    serverConfig.setName("gmsEbeanPrimaryServiceConfig");
    serverConfig.setDataSourceConfig(dataSourceConfig);
    serverConfig.setDefaultServer(false);
    serverConfig.setDdlGenerate(ebeanAutoCreate);
    serverConfig.setDdlRun(ebeanAutoCreate);
    return serverConfig;
  }
}
