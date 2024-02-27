package com.linkedin.datahub.upgrade.config;

import com.linkedin.datahub.upgrade.system.SystemUpdate;
import com.linkedin.datahub.upgrade.system.elasticsearch.BuildIndices;
import com.linkedin.datahub.upgrade.system.elasticsearch.CleanIndices;
import com.linkedin.datahub.upgrade.system.entity.steps.BackfillBrowsePathsV2;
import com.linkedin.datahub.upgrade.system.entity.steps.BackfillPolicyFields;
import com.linkedin.datahub.upgrade.system.via.ReindexDataJobViaNodesCLL;
import com.linkedin.gms.factory.common.TopicConventionFactory;
import com.linkedin.gms.factory.config.ConfigurationProvider;
import com.linkedin.gms.factory.kafka.DataHubKafkaProducerFactory;
import com.linkedin.gms.factory.kafka.schemaregistry.InternalSchemaRegistryFactory;
import com.linkedin.gms.factory.kafka.schemaregistry.SchemaRegistryConfig;
import com.linkedin.metadata.config.kafka.KafkaConfiguration;
import com.linkedin.metadata.dao.producer.KafkaEventProducer;
import com.linkedin.metadata.dao.producer.KafkaHealthChecker;
import com.linkedin.metadata.version.GitVersion;
import com.linkedin.mxe.TopicConvention;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.IndexedRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class SystemUpdateConfig {
  @Bean(name = "systemUpdate")
  public SystemUpdate systemUpdate(
      final BuildIndices buildIndices,
      final CleanIndices cleanIndices,
      @Qualifier("duheKafkaEventProducer") final KafkaEventProducer kafkaEventProducer,
      final GitVersion gitVersion,
      @Qualifier("revision") String revision,
      final BackfillBrowsePathsV2 backfillBrowsePathsV2,
      final ReindexDataJobViaNodesCLL reindexDataJobViaNodesCLL,
      final BackfillPolicyFields backfillPolicyFields) {

    String version = String.format("%s-%s", gitVersion.getVersion(), revision);
    return new SystemUpdate(
        buildIndices,
        cleanIndices,
        kafkaEventProducer,
        version,
        backfillBrowsePathsV2,
        reindexDataJobViaNodesCLL,
        backfillPolicyFields);
  }

  @Value("#{systemEnvironment['DATAHUB_REVISION'] ?: '0'}")
  private String revision;

  @Bean(name = "revision")
  public String getRevision() {
    return revision;
  }

  @Autowired
  @Qualifier(TopicConventionFactory.TOPIC_CONVENTION_BEAN)
  private TopicConvention topicConvention;

  @Autowired private KafkaHealthChecker kafkaHealthChecker;

  @Bean(name = "duheKafkaEventProducer")
  protected KafkaEventProducer duheKafkaEventProducer(
      @Qualifier("configurationProvider") ConfigurationProvider provider,
      KafkaProperties properties,
      @Qualifier("duheSchemaRegistryConfig") SchemaRegistryConfig duheSchemaRegistryConfig) {
    KafkaConfiguration kafkaConfiguration = provider.getKafka();
    Producer<String, IndexedRecord> producer =
        new KafkaProducer<>(
            DataHubKafkaProducerFactory.buildProducerProperties(
                duheSchemaRegistryConfig, kafkaConfiguration, properties));
    return new KafkaEventProducer(producer, topicConvention, kafkaHealthChecker);
  }

  /**
   * The ReindexDataJobViaNodesCLLConfig step requires publishing to MCL. Overriding the default
   * producer with this special producer which doesn't require an active registry.
   *
   * <p>Use when INTERNAL registry and is SYSTEM_UPDATE
   *
   * <p>This forces this producer into the EntityService
   */
  @Primary
  @Bean(name = "kafkaEventProducer")
  @Conditional(SystemUpdateCondition.class)
  @ConditionalOnProperty(
      name = "kafka.schemaRegistry.type",
      havingValue = InternalSchemaRegistryFactory.TYPE)
  protected KafkaEventProducer kafkaEventProducer(
      @Qualifier("duheKafkaEventProducer") KafkaEventProducer kafkaEventProducer) {
    return kafkaEventProducer;
  }
}
