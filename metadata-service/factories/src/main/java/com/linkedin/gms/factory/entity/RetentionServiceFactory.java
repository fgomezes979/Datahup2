package com.linkedin.gms.factory.entity;

import com.datastax.oss.driver.api.core.CqlSession;
import com.linkedin.gms.factory.spring.YamlPropertySourceFactory;
import com.linkedin.metadata.entity.EntityService;
import com.linkedin.metadata.entity.RetentionService;
import com.linkedin.metadata.entity.datastax.DatastaxRetentionService;
import com.linkedin.metadata.entity.ebean.EbeanRetentionService;
import io.ebean.EbeanServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.PropertySource;

import javax.annotation.Nonnull;


@Configuration
@PropertySource(value = "classpath:/application.yml", factory = YamlPropertySourceFactory.class)
public class RetentionServiceFactory {

  @Autowired
  @Qualifier("entityService")
  private EntityService _entityService;

  @Autowired(required = false)
  @Qualifier("ebeanServer")
  private EbeanServer _server;

  @Autowired(required = false)
  @Qualifier("datastaxSession")
  private CqlSession _cqlSession;

  @Value("${RETENTION_APPLICATION_BATCH_SIZE:1000}")
  private Integer _batchSize;


  @Bean(name = "retentionService")
  @DependsOn({"datastaxSession", "entityService"})
  @ConditionalOnProperty(name = "ENTITY_SERVICE_IMPL", havingValue = "datastax")
  @Nonnull
  protected RetentionService createDatastaxInstance() {
    RetentionService retentionService = new DatastaxRetentionService(_entityService, _cqlSession, _batchSize);
    _entityService.setRetentionService(retentionService);
    return retentionService;
  }


  @Bean(name = "retentionService")
  @DependsOn({"ebeanServer", "entityService"})
  @ConditionalOnProperty(name = "ENTITY_SERVICE_IMPL", havingValue = "ebean", matchIfMissing = true)
  @Nonnull
  protected RetentionService createEbeanInstance() {
    RetentionService retentionService = new EbeanRetentionService(_entityService, _server, _batchSize);
    _entityService.setRetentionService(retentionService);
    return retentionService;
  }
}
