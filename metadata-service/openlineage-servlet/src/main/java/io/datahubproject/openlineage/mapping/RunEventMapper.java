package io.datahubproject.openlineage.mapping;

import static io.datahubproject.openlineage.converter.OpenLineageToDataHub.*;

import com.linkedin.mxe.MetadataChangeProposal;
import datahub.event.EventFormatter;
import io.datahubproject.openlineage.config.DatahubOpenlineageConfig;
import io.openlineage.client.OpenLineage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RunEventMapper {

  public RunEventMapper() {}

  public Stream<MetadataChangeProposal> map(
      OpenLineage.RunEvent runEvent, RunEventMapper.MappingConfig mappingConfig) {
    EventFormatter eventFormatter = new EventFormatter();
    try {
      return convertRunEventToJob(runEvent, mappingConfig.getDatahubConfig())
          .toMcps(mappingConfig.datahubConfig)
          .stream()
          .map(
              mcp -> {
                try {
                  return eventFormatter.convert(mcp);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Builder
  @Getter
  public static class MappingConfig {
    DatahubOpenlineageConfig datahubConfig;
  }
}
