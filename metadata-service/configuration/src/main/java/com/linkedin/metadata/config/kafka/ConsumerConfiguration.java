package com.linkedin.metadata.config.kafka;

import lombok.Data;

@Data
public class ConsumerConfiguration {

  private int maxPartitionFetchBytes;
  private boolean stopOnDeserializationError;
  private boolean healthCheckEnabled;
  private int sessionTimeout;
  private int maxPollRecords;
  private int maxPollInterval;
}
